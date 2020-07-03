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

import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.dispatcher.*;
import org.apache.flink.runtime.entrypoint.ClusterEntrypoint;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.rpc.RpcService;
import org.apache.flink.runtime.webmonitor.retriever.LeaderGatewayRetriever;
import org.apache.flink.shaded.guava18.com.google.common.collect.Iterables;

import java.util.Collection;

import static org.apache.flink.runtime.entrypoint.ClusterEntrypoint.EXECUTION_MODE;

/**
 * {@link DispatcherFactory} which creates a {@link MiniDispatcher}.
 */
public enum JobStreamManagerDispatcherFactory implements StreamManagerDispatcherFactory {
	INSTANCE;

	@Override
//	public MiniStreamManagerDispatcher createStreamManagerDispatcher(
	public StreamManagerDispatcher createStreamManagerDispatcher(
		RpcService rpcService,
		StreamManagerDispatcherId fencingToken,
		Collection<JobGraph> recoveredJobs,
		PartialStreamManagerDispatcherServicesWithJobGraphStore partialDispatcherServicesWithJobGraphStore,
		LeaderGatewayRetriever<DispatcherGateway> dispatcherGatewayRetriever) throws Exception {
		final JobGraph jobGraph = Iterables.getOnlyElement(recoveredJobs);

		final Configuration configuration = partialDispatcherServicesWithJobGraphStore.getConfiguration();
		final String executionModeValue = configuration.getString(EXECUTION_MODE);
		final ClusterEntrypoint.ExecutionMode executionMode = ClusterEntrypoint.ExecutionMode.valueOf(executionModeValue);

//		return new MiniStreamManagerDispatcher(
//			rpcService,
//			getEndpointId(),
//			fencingToken,
//			StreamManagerDispatcherServices.from(partialDispatcherServicesWithJobGraphStore, DefaultJobManagerRunnerFactory.INSTANCE),
//			jobGraph,
//			executionMode);
		return null;
	}
}
