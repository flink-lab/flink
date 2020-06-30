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

import org.apache.flink.runtime.controlplane.dispatcher.PartialStreamManagerDispatcherServices;
import org.apache.flink.runtime.controlplane.dispatcher.StreamManagerDispatcherFactory;
import org.apache.flink.runtime.jobmanager.JobGraphStoreFactory;
import org.apache.flink.runtime.rpc.FatalErrorHandler;
import org.apache.flink.runtime.rpc.RpcService;

import java.util.concurrent.Executor;

/**
 * Factory for the {@link SessionStreamManagerDispatcherLeaderProcessFactory}.
 */
public class SessionStreamManagerDispatcherLeaderProcessFactoryFactory implements StreamManagerDispatcherLeaderProcessFactoryFactory {

	private final StreamManagerDispatcherFactory dispatcherFactory;

	private SessionStreamManagerDispatcherLeaderProcessFactoryFactory(StreamManagerDispatcherFactory dispatcherFactory) {
		this.dispatcherFactory = dispatcherFactory;
	}

	@Override
	public StreamManagerDispatcherLeaderProcessFactory createFactory(
			JobGraphStoreFactory jobGraphStoreFactory,
			Executor ioExecutor,
			RpcService rpcService,
			PartialStreamManagerDispatcherServices partialDispatcherServices,
			FatalErrorHandler fatalErrorHandler) {
		final AbstractStreamManagerDispatcherLeaderProcess.StreamManagerDispatcherGatewayServiceFactory dispatcherGatewayServiceFactory = new DefaultStreamManagerDispatcherGatewayServiceFactory(
			dispatcherFactory,
			rpcService,
			partialDispatcherServices);

		return new SessionStreamManagerDispatcherLeaderProcessFactory(
			dispatcherGatewayServiceFactory,
			jobGraphStoreFactory,
			ioExecutor,
			fatalErrorHandler);
	}

	public static SessionStreamManagerDispatcherLeaderProcessFactoryFactory create(StreamManagerDispatcherFactory dispatcherFactory) {
		return new SessionStreamManagerDispatcherLeaderProcessFactoryFactory(dispatcherFactory);
	}
}
