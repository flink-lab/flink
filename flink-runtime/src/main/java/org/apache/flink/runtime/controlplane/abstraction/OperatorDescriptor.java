package org.apache.flink.runtime.controlplane.abstraction;

import org.apache.flink.annotation.Internal;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.api.common.functions.Function;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.runtime.rescale.JobRescaleCoordinator;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

import static org.apache.flink.runtime.controlplane.abstraction.ExecutionPlan.*;

/**
 * this class make sure all field is not modifiable for external class
 */
public class OperatorDescriptor {
	private static final Logger LOG = LoggerFactory.getLogger(OperatorDescriptor.class);

	private final int operatorID;
	private final String name;

	private final Set<OperatorDescriptor> parents = new HashSet<>();
	private final Set<OperatorDescriptor> children = new HashSet<>();

	private final TaskConfigurations taskConfigurations;
	private boolean stateful = false;

	public OperatorDescriptor(int operatorID, String name, int parallelism, Map<Integer, TaskDescriptor> tasks) {
		this.operatorID = operatorID;
		this.name = name;
		this.taskConfigurations = new TaskConfigurations(parallelism, tasks);
	}

	public int getOperatorID() {
		return operatorID;
	}

	public List<Integer> getTaskIds() {
		return new ArrayList<>(this.taskConfigurations.tasks.keySet());
	}

	public String getName() {
		return name;
	}

	public Set<OperatorDescriptor> getParents() {
		return Collections.unmodifiableSet(parents);
	}

	public Set<OperatorDescriptor> getChildren() {
		return Collections.unmodifiableSet(children);
	}

	public int getParallelism() {
		return taskConfigurations.parallelism;
	}

	public Function getUdf() {
		Object functionObject = taskConfigurations.executionLogic.attributeMap.get(ExecutionLogic.UDF);
		return (Function) Preconditions.checkNotNull(functionObject);
	}


	public Map<Integer, List<Integer>> getKeyStateAllocation() {
		return taskConfigurations.keyStateAllocation;
	}

	public Map<Integer, Map<Integer, List<Integer>>> getKeyMapping() {
		return Collections.unmodifiableMap(taskConfigurations.keyMapping);
	}

	public void setParallelism(int parallelism) {
		taskConfigurations.parallelism = parallelism;
	}

	public void setUdf(Function udf) {
		taskConfigurations.executionLogic.udf = udf;
	}

	public final void setControlAttribute(String name, Object obj) throws Exception {
		taskConfigurations.executionLogic.updateField(name, obj);
	}

	public Map<String, Object> getControlAttributeMap() {
		return Collections.unmodifiableMap(taskConfigurations.executionLogic.attributeMap);
	}

	@Internal
//	void setKeyStateAllocation(List<List<Integer>> keyStateAllocation) {
	void setKeyStateAllocation(Map<Integer, List<Integer>> keyStateAllocation) {
		addAll(keyStateAllocation);
		for (OperatorDescriptor parent : parents) {
			parent.taskConfigurations.keyMapping.put(operatorID, keyStateAllocation);
		}
		// stateless operator should not be allocated  key set
		stateful = !taskConfigurations.keyStateAllocation.isEmpty();
	}

	private void addAll(Map<Integer, List<Integer>> keyStateAllocation) {
//		payload.keyStateAllocation.clear();
		Map<Integer, List<Integer>> unmodifiable = new HashMap<>();
		for (int taskId : keyStateAllocation.keySet()) {
			unmodifiable.put(taskId, Collections.unmodifiableList(keyStateAllocation.get(taskId)));
		}
		taskConfigurations.keyStateAllocation = Collections.unmodifiableMap(unmodifiable);
	}

	@Internal
	void setKeyMapping(Map<Integer, Map<Integer, List<Integer>>> keyMapping) {
		Map<Integer, Map<Integer, List<Integer>>> unmodifiable = convertKeyMappingToUnmodifiable(keyMapping);
		taskConfigurations.keyMapping.putAll(unmodifiable);
		for (OperatorDescriptor child : children) {
			if (child.stateful) {
				// todo two inputs?
				child.addAll(keyMapping.get(child.operatorID));
			}
		}
	}

	@Internal
	ExecutionLogic getApplicationLogic(){
		return taskConfigurations.executionLogic;
	}

	private Map<Integer, Map<Integer, List<Integer>>> convertKeyMappingToUnmodifiable(Map<Integer, Map<Integer, List<Integer>>> keyMappings) {
		Map<Integer, Map<Integer, List<Integer>>> unmodifiable = new HashMap<>();
		for (Integer inOpID : keyMappings.keySet()) {
			Map<Integer, List<Integer>> keyStateAllocation = convertKeyStateToUnmodifiable(keyMappings.get(inOpID));

			Map<Integer, List<Integer>> unmodifiableKeys = Collections.unmodifiableMap(keyStateAllocation);
			unmodifiable.put(inOpID, unmodifiableKeys);
		}
		return unmodifiable;
	}

	private Map<Integer, List<Integer>> convertKeyStateToUnmodifiable(Map<Integer, List<Integer>> keyStateAllocation) {
		Map<Integer, List<Integer>> newMap = new HashMap<>();
		for (Integer taskId : keyStateAllocation.keySet()) {
			newMap.put(taskId, Collections.unmodifiableList(newMap.get(taskId)));
		}
		return newMap;
	}

	/**
	 * we assume that the key operator only have one upstream opeartor
	 *
	 * @param keyStateAllocation
	 */
	@PublicEvolving
	public void updateKeyStateAllocation(Map<Integer, List<Integer>> keyStateAllocation) {
		if (!stateful) {
			System.out.println("not support now");
			return;
		}
		try {
			addAll(keyStateAllocation);
			// sync with parent's key mapping
			for(OperatorDescriptor parent: parents) {
				parent.taskConfigurations.keyMapping.put(operatorID, keyStateAllocation);
			}
		} catch (Exception e) {
			LOG.info("error while set key state allocation", e);
		}
	}

	/**
	 * Only use to update key mapping for case that target operator is stateless.
	 * If the target is stateful, the key mapping should be changed by
	 * setKeyStateAllocation(List<List<Integer>> keyStateAllocation);
	 *
	 * @param targetOperatorID
	 * @param keyMapping
	 */
	@PublicEvolving
	public void updateKeyMapping(int targetOperatorID, Map<Integer, List<Integer>> keyMapping) {
		try {
			OperatorDescriptor child = checkOperatorIDExistInSet(targetOperatorID, children);
			taskConfigurations.keyMapping.put(targetOperatorID, keyMapping);
			if (child.stateful) {
				child.addAll(keyMapping);
			}
		} catch (Exception e) {
			LOG.info("error while set key output keymapping", e);
		}
	}

	private static OperatorDescriptor checkOperatorIDExistInSet(int opID, Set<OperatorDescriptor> set) throws Exception {
		for (OperatorDescriptor descriptor : set) {
			if (opID == descriptor.getOperatorID()) {
				return descriptor;
			}
		}
		throw new Exception("do not have this id in set");
	}


	/**
	 * @param childEdges       the list of pair of parent id and child id to represent the relationship between operator
	 * @param allOperatorsById
	 */
	void addChildren(List<Tuple2<Integer, Integer>> childEdges, Map<Integer, OperatorDescriptor> allOperatorsById) {
		// f0 is parent operator id, f1 is child operator id
		for (Tuple2<Integer, Integer> edge : childEdges) {
			Preconditions.checkArgument(allOperatorsById.get(edge.f0) == this, "edge source is wrong matched");
			OperatorDescriptor descriptor = allOperatorsById.get(edge.f1);
			// I think I am your father
			children.add(descriptor);
			descriptor.parents.add(this);
		}
	}

	/**
	 * @param parentEdges      the list of pair of parent id and child id to represent the relationship between operator
	 * @param allOperatorsById
	 */
	void addParent(List<Tuple2<Integer, Integer>> parentEdges, Map<Integer, OperatorDescriptor> allOperatorsById) {
		// f0 is parent operator id, f1 is child operator id
		for (Tuple2<Integer, Integer> edge : parentEdges) {
			Preconditions.checkArgument(allOperatorsById.get(edge.f1) == this, "edge source is wrong matched");
			OperatorDescriptor descriptor = allOperatorsById.get(edge.f0);
			// I think I am your father
			parents.add(descriptor);
			descriptor.children.add(this);
		}
	}

	@Override
	public String toString() {
		return "OperatorDescriptor{name='" + name + "'', parallelism=" + taskConfigurations.parallelism +
			", parents:" + parents.size() + ", children:" + children.size() + '}';
	}

	/**
	 * The object to store all task configurations under the operator
	 * The configurations are stored per operator for the convenience of finding siblings of each task.
	 */
	private static class TaskConfigurations {
		int parallelism;
		final ExecutionLogic executionLogic;
		/* for stateful one input stream task, the key state allocation item is always one */

		// taskId -> keyset
		public Map<Integer, List<Integer>> keyStateAllocation;
		// task id -> key mapping
		public Map<Integer, Map<Integer, List<Integer>>> keyMapping;
		// task id -> task(slots, location)
		public Map<Integer, TaskDescriptor> tasks;

		TaskConfigurations(int parallelism, Map<Integer, TaskDescriptor> tasks) {
			this.parallelism = parallelism;
			keyStateAllocation = new HashMap<>();
			keyMapping = new HashMap<>();
			executionLogic = new ExecutionLogic();
			this.tasks = tasks;
		}
	}

	void setAttributeField(Object object, List<Field> fieldList) throws IllegalAccessException {
		taskConfigurations.executionLogic.operator = object;
		for(Field field: fieldList) {
			ControlAttribute attribute = field.getAnnotation(ControlAttribute.class);
			boolean accessible = field.isAccessible();
			// temporary set true
			field.setAccessible(true);
			taskConfigurations.executionLogic.fields.put(attribute.name(), field);
			taskConfigurations.executionLogic.attributeMap.put(attribute.name(), field.get(object));
			field.setAccessible(accessible);
		}
	}

	public static class ExecutionLogic {

		public static final String UDF = "UDF";
		public static final String OPERATOR_TYPE = "OPERATOR_TYPE";

		private final Map<String, Object> attributeMap = new HashMap<>();
		private final Map<String, Field> fields = new HashMap<>();
		private Function udf;

		@VisibleForTesting
		private Object operator;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ExecutionLogic that = (ExecutionLogic) o;
			return attributeMap.equals(that.attributeMap);
		}

		@Override
		public int hashCode() {
			return Objects.hash(attributeMap);
		}

		public ExecutionLogic copyTo(ExecutionLogic that){
			that.attributeMap.clear();
			that.attributeMap.putAll(attributeMap);
			return that;
		}

		public Map<String, Object> getControlAttributeMap() {
			return Collections.unmodifiableMap(attributeMap);
		}

		public Map<String, Field> getControlAttributeFieldMap() {
			return Collections.unmodifiableMap(fields);
		}

		private void updateField(String name, Object obj) throws Exception {
			Field field = fields.get(name);
			boolean access = field.isAccessible();
			try {
				field.setAccessible(true);
				field.set(operator, obj);
				attributeMap.put(name, obj);
			} catch (IllegalAccessException e) {
				LOG.info("error while update field", e);
				throw new Exception("update field fail", e);
			} finally {
				field.setAccessible(access);
			}
		}
	}

}
