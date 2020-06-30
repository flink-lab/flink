/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.controlplane.dispatcher.runner;

import org.apache.flink.runtime.clusterframework.ApplicationStatus;
import org.apache.flink.runtime.concurrent.FutureUtils;
import org.apache.flink.runtime.leaderelection.LeaderContender;
import org.apache.flink.runtime.leaderelection.LeaderElectionService;
import org.apache.flink.runtime.rpc.FatalErrorHandler;
import org.apache.flink.util.FlinkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Runner for the {@link org.apache.flink.runtime.dispatcher.Dispatcher} which is responsible for the
 * leader election.
 */
public final class DefaultStreamManagerDispatcherRunner implements StreamManagerDispatcherRunner, LeaderContender {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultStreamManagerDispatcherRunner.class);

	private final Object lock = new Object();

	private final LeaderElectionService leaderElectionService;

	private final FatalErrorHandler fatalErrorHandler;

	private final StreamManagerDispatcherLeaderProcessFactory streamManagerDispatcherLeaderProcessFactory;

	private final CompletableFuture<Void> terminationFuture;

	private final CompletableFuture<ApplicationStatus> shutDownFuture;

	private boolean running;

	private StreamManagerDispatcherLeaderProcess streamManagerDispatcherLeaderProcess;

	private CompletableFuture<Void> previousDispatcherLeaderProcessTerminationFuture;

	private DefaultStreamManagerDispatcherRunner(
			LeaderElectionService leaderElectionService,
			FatalErrorHandler fatalErrorHandler,
			StreamManagerDispatcherLeaderProcessFactory streamManagerDispatcherLeaderProcessFactory) {
		this.leaderElectionService = leaderElectionService;
		this.fatalErrorHandler = fatalErrorHandler;
		this.streamManagerDispatcherLeaderProcessFactory = streamManagerDispatcherLeaderProcessFactory;
		this.terminationFuture = new CompletableFuture<>();
		this.shutDownFuture = new CompletableFuture<>();

		this.running = true;
		this.streamManagerDispatcherLeaderProcess = StoppedStreamManagerDispatcherLeaderProcess.INSTANCE;
		this.previousDispatcherLeaderProcessTerminationFuture = CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<ApplicationStatus> getShutDownFuture() {
		return shutDownFuture;
	}

	@Override
	public CompletableFuture<Void> closeAsync() {
		synchronized (lock) {
			if (!running) {
				return terminationFuture;
			} else {
				running = false;
			}
		}

		stopDispatcherLeaderProcess();

		FutureUtils.forward(
			previousDispatcherLeaderProcessTerminationFuture,
			terminationFuture);

		return terminationFuture;
	}

	// ---------------------------------------------------------------
	// Leader election
	// ---------------------------------------------------------------

	@Override
	public void grantLeadership(UUID leaderSessionID) {
		runActionIfRunning(() -> startNewDispatcherLeaderProcess(leaderSessionID));
	}

	private void startNewDispatcherLeaderProcess(UUID leaderSessionID) {
		stopDispatcherLeaderProcess();

		streamManagerDispatcherLeaderProcess = createNewDispatcherLeaderProcess(leaderSessionID);

		final StreamManagerDispatcherLeaderProcess newStreamManagerDispatcherLeaderProcess = streamManagerDispatcherLeaderProcess;
		FutureUtils.assertNoException(
			previousDispatcherLeaderProcessTerminationFuture.thenRun(newStreamManagerDispatcherLeaderProcess::start));
	}

	private void stopDispatcherLeaderProcess() {
		final CompletableFuture<Void> terminationFuture = streamManagerDispatcherLeaderProcess.closeAsync();
		previousDispatcherLeaderProcessTerminationFuture = FutureUtils.completeAll(
			Arrays.asList(
				previousDispatcherLeaderProcessTerminationFuture,
				terminationFuture));
	}

	private StreamManagerDispatcherLeaderProcess createNewDispatcherLeaderProcess(UUID leaderSessionID) {
		LOG.debug("Create new {} with leader session id {}.", StreamManagerDispatcherLeaderProcess.class.getSimpleName(), leaderSessionID);

		final StreamManagerDispatcherLeaderProcess newStreamManagerDispatcherLeaderProcess = streamManagerDispatcherLeaderProcessFactory.create(leaderSessionID);

		forwardShutDownFuture(newStreamManagerDispatcherLeaderProcess);
		forwardConfirmLeaderSessionFuture(leaderSessionID, newStreamManagerDispatcherLeaderProcess);

		return newStreamManagerDispatcherLeaderProcess;
	}

	private void forwardShutDownFuture(StreamManagerDispatcherLeaderProcess newStreamManagerDispatcherLeaderProcess) {
		newStreamManagerDispatcherLeaderProcess.getShutDownFuture().whenComplete(
			(applicationStatus, throwable) -> {
				synchronized (lock) {
					// ignore if no longer running or if leader processes is no longer valid
					if (running && this.streamManagerDispatcherLeaderProcess == newStreamManagerDispatcherLeaderProcess) {
						if (throwable != null) {
							shutDownFuture.completeExceptionally(throwable);
						} else {
							shutDownFuture.complete(applicationStatus);
						}
					}
				}
			});
	}

	private void forwardConfirmLeaderSessionFuture(UUID leaderSessionID, StreamManagerDispatcherLeaderProcess newStreamManagerDispatcherLeaderProcess) {
		FutureUtils.assertNoException(
			newStreamManagerDispatcherLeaderProcess.getLeaderAddressFuture().thenAccept(
				leaderAddress -> {
					if (leaderElectionService.hasLeadership(leaderSessionID)) {
						leaderElectionService.confirmLeadership(leaderSessionID, leaderAddress);
					}
				}));
	}

	@Override
	public void revokeLeadership() {
		runActionIfRunning(this::stopDispatcherLeaderProcess);
	}

	private void runActionIfRunning(Runnable runnable) {
		synchronized (lock) {
			if (running) {
				runnable.run();
			} else {
				LOG.debug("Ignoring action because {} has already been stopped.", getClass().getSimpleName());
			}
		}
	}

	@Override
	public void handleError(Exception exception) {
		fatalErrorHandler.onFatalError(
			new FlinkException(
				String.format("Exception during leader election of %s occurred.", getClass().getSimpleName()),
				exception));
	}

	public static StreamManagerDispatcherRunner create(
			LeaderElectionService leaderElectionService,
			FatalErrorHandler fatalErrorHandler,
			StreamManagerDispatcherLeaderProcessFactory streamManagerDispatcherLeaderProcessFactory) throws Exception {
		final DefaultStreamManagerDispatcherRunner dispatcherRunner = new DefaultStreamManagerDispatcherRunner(
			leaderElectionService,
			fatalErrorHandler,
			streamManagerDispatcherLeaderProcessFactory);
		return StreamManagerDispatcherRunnerLeaderElectionLifecycleManager.createFor(dispatcherRunner, leaderElectionService);
	}
}
