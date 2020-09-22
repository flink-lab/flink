package org.apache.flink.streaming.controlplane.reconfigure.operator;

import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.streaming.api.graph.StreamConfig;
import org.apache.flink.streaming.api.operators.ChainingStrategy;
import org.apache.flink.streaming.api.operators.Output;
import org.apache.flink.streaming.api.operators.StreamOperator;
import org.apache.flink.streaming.api.operators.StreamOperatorFactory;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.runtime.tasks.StreamTask;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Simple factory for the Update Operator
 */
public class UpdatedOperatorFactory<IN, OUT> implements StreamOperatorFactory<OUT> {

	// use to check whether this operator update is valid
	OperatorDescriptor descriptor;

	private UpdatedOperator<IN, OUT> operator = null;
	private final ControlFunction function;

	public UpdatedOperatorFactory(OperatorID operatorID, JobGraph jobGraph, ControlFunction function) {
		// using default control function
		descriptor = new OperatorDescriptor(operatorID, jobGraph);
		this.function = function;
	}

	protected UpdatedOperator<IN, OUT> create(ControlFunction function) {
		return new UpdatedOperator<>(function);
	}


	public StreamOperator<OUT> getOperator() {
		if(operator==null){
			operator = create(this.function);
		}
		return checkNotNull(operator, "operator not set...");
	}

	@Override
	public <T extends StreamOperator<OUT>> T createStreamOperator(
		StreamTask<?, ?> containingTask,
		StreamConfig config,
		Output<StreamRecord<OUT>> output) {
		return null;
	}

	@Override
	public void setChainingStrategy(ChainingStrategy strategy) {
		operator.setChainingStrategy(strategy);
	}

	@Override
	public ChainingStrategy getChainingStrategy() {
		return operator.getChainingStrategy();
	}

	@Override
	public Class<? extends StreamOperator> getStreamOperatorClass(ClassLoader classLoader) {
		return operator.getClass();
	}

}
