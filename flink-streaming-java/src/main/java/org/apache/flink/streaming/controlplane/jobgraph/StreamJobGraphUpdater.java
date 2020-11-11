package org.apache.flink.streaming.controlplane.jobgraph;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.api.common.functions.Function;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobVertex;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.streaming.api.graph.StreamConfig;
import org.apache.flink.streaming.api.operators.StreamOperatorFactory;
import org.apache.flink.runtime.rescale.reconfigure.JobGraphRescaler;
import org.apache.flink.runtime.rescale.reconfigure.JobGraphUpdater;
import org.apache.flink.streaming.controlplane.reconfigure.operator.ControlFunction;
import org.apache.flink.streaming.controlplane.reconfigure.operator.ControlOperatorFactory;
import org.apache.flink.streaming.controlplane.rescale.StreamJobGraphRescaler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class StreamJobGraphUpdater implements JobGraphRescaler, JobGraphUpdater {

	private final JobGraphRescaler jobGraphRescaler;
	private JobGraph jobGraph;
	private ClassLoader userClassLoader;

	private Map<Integer, OperatorID> operatorIDMap;

	public StreamJobGraphUpdater(JobGraph jobGraph, ClassLoader userClassLoader) {
		this.jobGraphRescaler = new StreamJobGraphRescaler(jobGraph, userClassLoader);
		this.jobGraph = jobGraph;
		this.userClassLoader = userClassLoader;
		operatorIDMap = new HashMap<>();
		for (JobVertex vertex : jobGraph.getVertices()) {
			StreamConfig streamConfig = new StreamConfig(vertex.getConfiguration());
			Map<Integer, StreamConfig> configMap = streamConfig.getTransitiveChainedTaskConfigsWithSelf(userClassLoader);
			for (StreamConfig config : configMap.values()) {
				operatorIDMap.put(config.getVertexID(), config.getOperatorID());
			}
		}
	}

	@Override
	public Tuple2<List<JobVertexID>, List<JobVertexID>> rescale(JobVertexID id, int newParallelism, Map<Integer, List<Integer>> partitionAssignment) {
		return jobGraphRescaler.rescale(id, newParallelism, partitionAssignment);
	}

	@Override
	public Tuple2<List<JobVertexID>, List<JobVertexID>> repartition(JobVertexID id, Map<Integer, List<Integer>> partitionAssignment) {
		return jobGraphRescaler.repartition(id, partitionAssignment);
	}

	@Override
	public String print(Configuration config) {
		return jobGraphRescaler.print(config);
	}

	@Override
	public Map<Integer, OperatorID> getOperatorIDMap() {
		return operatorIDMap;
	}

	@Override
	public <OUT> JobVertexID updateOperator(int vertexId, Function function) throws Exception {
		OperatorID operatorID = operatorIDMap.get(vertexId);
		if(function instanceof ControlFunction){
			ControlOperatorFactory<?, ?> factory = new ControlOperatorFactory<>(
				vertexId, (ControlFunction) function
			);
			return this.updateOperator(operatorID, factory);
		}
		throw new Exception("only support substitue control function now");
	}

	public <OUT> JobVertexID updateOperator(OperatorID operatorID, StreamOperatorFactory<OUT> operatorFactory) throws Exception {
		return updateOperatorConfiguration(operatorID, config -> config.setStreamOperatorFactory(operatorFactory));
	}

	/**
	 * update operators' configuration ==> stream config using @code UpdateCallBack
	 *
	 * @param operatorID the id of target operator
	 * @param callBack   the call back using to update configuration
	 * @return true if updated in chained operator otherwise false
	 * @throws Exception if do not found corresponding operator
	 */
	private JobVertexID updateOperatorConfiguration(OperatorID operatorID, Consumer<StreamConfig> callBack) throws Exception {
		for (JobVertex vertex : jobGraph.getVertices()) {
			for (OperatorID id : vertex.getOperatorIDs()) {
				if (id.equals(operatorID)) {
					if (vertex.getOperatorIDs().size() > 1) {
						// there exists chained operators in this job vertex
						updateConfigInChainedOperators(vertex.getConfiguration(), operatorID, callBack);
					} else {
						callBack.accept(new StreamConfig(vertex.getConfiguration()));
					}
					return vertex.getID();
				}
			}
		}
		throw new Exception("do not found target job vertex has this operator id");
	}

	private void updateConfigInChainedOperators(Configuration configuration, OperatorID operatorID, Consumer<StreamConfig> callBack) throws Exception {
		StreamConfig streamConfig = new StreamConfig(configuration);
		Map<Integer, StreamConfig> configMap = streamConfig.getTransitiveChainedTaskConfigs(userClassLoader);
		for (StreamConfig config : configMap.values()) {
			if (operatorID.equals(config.getOperatorID())) {
				callBack.accept(config);
				streamConfig.setTransitiveChainedTaskConfigs(configMap);
				return;
			}
		}
		throw new Exception("do not found target stream config with this operator id");
	}

	@VisibleForTesting
	private StreamConfig findStreamConfig(OperatorID operatorID) throws Exception {
		for (JobVertex vertex : jobGraph.getVertices()) {
			StreamConfig streamConfig = new StreamConfig(vertex.getConfiguration());
			Map<Integer, StreamConfig> configMap = streamConfig.getTransitiveChainedTaskConfigsWithSelf(userClassLoader);
			for (StreamConfig config : configMap.values()) {
				if (operatorID.equals(config.getOperatorID())) {
					return config;
				}
			}
		}
		throw new Exception("do not found target stream config with this operator id");
	}

}
