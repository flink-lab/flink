package org.apache.flink.runtime.controlplane.abstraction;


import org.apache.flink.api.java.tuple.Tuple2;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * todo the interface is put in Flink runtime, seems a little not natural. another solution is
 * put it on the third module (may call streammanger-common), make both our stream manager and flink runtime import this module
 *
 */
public interface ExecutionPlan {
	/**
	 * Get all hosts of current job
	 *
	 * @return
	 */
	Node[] getResourceDistribution();

	/**
	 * Get one of the parallel task of one operator.
	 *
	 * @param operatorID the operator id of this operator
	 * @param taskId     represent which task instance of this operator
	 * @return
	 */
	TaskDescriptor getTask(Integer operatorID, int taskId);

	class TaskDescriptor {
		// task own threads
		int allocatedSlot;
		Node location;
		// TODO: add the config here.

		public TaskDescriptor(int allocatedSlot, Node location) {
			this.allocatedSlot = allocatedSlot;
			this.location = location;
			// taskId = <Operator, taskIdx>
			location.addContainedTask(this);
		}
	}

	class Node {
//		// host network address
//		InetAddress address;
//		// host number of cpus
//		int numCpus;
//		/* host memory in bytes */
//		int memory;

		// address id
		InetAddress nodeAddress;
		// number of slots
		int numOfSlots;

		// taskId = <Operator, taskIdx>
		List<TaskDescriptor> deployedTasks;

		public Node(InetAddress nodeAddress, int numOfSlots) {
//			this.address = address;
//			this.numCpus = numCpus;
//			this.memory = memory;
			this.nodeAddress = nodeAddress;
			this.numOfSlots = numOfSlots;
			deployedTasks = new ArrayList<>();
		}

		void addContainedTask(TaskDescriptor task){
			deployedTasks.add(task);
		}
	}

	/**
	 * Return UserFunction, StreamOperator, or StreamOperatorFactory?
	 * The Function type belongs to Flink, should we use a new data structure since we want to decouple
	 *
	 * @param operatorID the operator id of this operator
	 * @return
	 */
	org.apache.flink.api.common.functions.Function getUserFunction(Integer operatorID);

	/**
	 * To get how the input key state was allocated among the sub operator instance.
	 * Map(sourceVertexId -> [operator index, [assigned keys]])
	 *
	 * Multiple sourceVertexId means there are multiple input edges.
	 *
	 * @param operatorID the target key mapping of operator we want to know
	 * @return A map from streamEdgeId and the key mapping of this stream edge, the map value is a list o list
	 * representing the output channel with its assigned keys.
	 * @throws Exception
	 */
	Map<Integer, List<Integer>> getKeyStateAllocation(Integer operatorID);

	/**
	 * Return how the key mapping to down stream operator.
	 * Map(targetVertexId -> [target operator index, [assigned keys]])
	 *
	 * Multiple targetVertex means there are multiple output edges.
	 *
	 * @param operatorID the target key mapping of operator we want to know
	 * @return A map from streamEdgeId and the key mapping of this stream edge, the map value is a list o list
	 * representing the output channel with its assigned keys.
	 * @throws Exception
	 */
	Map<Integer, Map<Integer, List<Integer>>> getKeyMapping(Integer operatorID);

	/**
	 * Return the parallelism of operator with given operator id
	 * @param operatorID
	 * @return
	 */
	int getParallelism(Integer operatorID);

	Iterator<OperatorDescriptor> getAllOperator();

	OperatorDescriptor getOperatorByID(Integer operatorID);

	ExecutionPlan redistribute(Integer operatorID, Map<Integer, List<Integer>> distribution);

	ExecutionPlan updateExecutionLogic(Integer operatorID, Object function);

	ExecutionPlan reDeploy(Integer operatorID, @Nullable Map<Integer, List<Tuple2<Integer, Node>>> deployment, Boolean isCreate);

	ExecutionPlan update(Function<ExecutionPlan, ExecutionPlan> applier);

	Map<String, Map<Integer, List<Integer>>> getTransformations();
}
