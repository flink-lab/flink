package org.apache.flink.streaming.controlplane.reconfigure;

import org.apache.flink.runtime.jobgraph.OperatorID;
import org.apache.flink.streaming.controlplane.reconfigure.operator.ControlFunction;
import org.apache.flink.streaming.controlplane.reconfigure.operator.ControlOperatorFactory;
import org.apache.flink.streaming.controlplane.reconfigure.type.FunctionTypeStorage;
import org.apache.flink.streaming.controlplane.reconfigure.type.InMemoryFunctionStorge;
import org.apache.flink.streaming.controlplane.streammanager.insts.PrimitiveInstruction;
import org.apache.flink.streaming.controlplane.udm.AbstractControlPolicy;

/**
 * Implement Function Transfer
 */
public abstract class ControlFunctionManager extends AbstractControlPolicy implements ControlFunctionManagerService {

	private FunctionTypeStorage functionTypeStorage;

	ControlFunctionManager(PrimitiveInstruction primitiveInstruction) {
		super(primitiveInstruction);
		this.functionTypeStorage = new InMemoryFunctionStorge();
	}

	public abstract void startControllerInternal();


	/**
	 * we don't know how to register new function yet
	 *
	 * @param function target control function
	 */
	@Override
	public void registerFunction(ControlFunction function) {
		functionTypeStorage.addFunctionType(function.getClass());
	}

	@Override
	public void reconfigure(OperatorID operatorID, ControlFunction function) {
		System.out.println(System.currentTimeMillis() + ":Substitute `Control` Function...");
		ControlOperatorFactory<?, ?> operatorFactory = new ControlOperatorFactory<>(
			operatorID,
			getInstructionSet().getStreamJobState().getJobGraph(),
			function);
		try {
			// since job graph is shared in stream manager and among its services, we don't need to pass it
			getInstructionSet().changeOperator(operatorID, operatorFactory, this);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
