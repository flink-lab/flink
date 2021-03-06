/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.controlplane.dispatcher;

import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.dispatcher.Dispatcher;
import org.apache.flink.runtime.dispatcher.DispatcherId;
import org.apache.flink.runtime.dispatcher.DispatcherServices;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.rpc.RpcService;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * {@link Dispatcher} implementation used for testing purposes.
 */
class TestingDispatcher extends Dispatcher {

	private final CompletableFuture<Void> startFuture;

	TestingDispatcher(
			RpcService rpcService,
			String endpointId,
			DispatcherId fencingToken,
			Collection<JobGraph> recoveredJobs,
			DispatcherServices dispatcherServices) throws Exception {
		super(
			rpcService,
			endpointId,
			fencingToken,
			recoveredJobs,
			dispatcherServices);

		this.startFuture = new CompletableFuture<>();
	}

	@Override
	public void onStart() throws Exception {
		try {
			super.onStart();
		} catch (Exception e) {
			startFuture.completeExceptionally(e);
			throw e;
		}

		startFuture.complete(null);
	}

//	void completeJobExecution(ArchivedExecutionGraph archivedExecutionGraph) {
//		runAsync(
//			() -> jobReachedGloballyTerminalState(archivedExecutionGraph));
//	}
//
//	CompletableFuture<Void> getJobTerminationFuture(@Nonnull JobID jobId, @Nonnull Time timeout) {
//		return callAsyncWithoutFencing(
//			() -> getJobTerminationFuture(jobId),
//			timeout).thenCompose(Function.identity());
//	}

	CompletableFuture<Integer> getNumberJobs(Time timeout) {
		return callAsyncWithoutFencing(
			() -> listJobs(timeout).get().size(),
			timeout);
	}

	void waitUntilStarted() {
		startFuture.join();
	}
}
