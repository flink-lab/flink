package org.apache.flink.streaming.controlplane.udm;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.controlplane.abstraction.ExecutionPlan;
import org.apache.flink.runtime.controlplane.abstraction.OperatorDescriptor;
import org.apache.flink.streaming.controlplane.streammanager.abstraction.ReconfigurationExecutor;

import java.util.*;

import static org.apache.flink.util.Preconditions.checkState;

public class StockController extends AbstractController {
	private final Object lock = new Object();
	private final Profiler profiler;
	private final Map<String, String> experimentConfig;

	public final static String TEST_OPERATOR_NAME = "trisk.reconfig.operator.name";


	public StockController(ReconfigurationExecutor reconfigurationExecutor, Configuration configuration) {
		super(reconfigurationExecutor);
		profiler = new Profiler();
		experimentConfig = configuration.toMap();
	}

	@Override
	public synchronized void startControllers() {
		System.out.println("PerformanceMeasure is starting...");
		profiler.setName("reconfiguration performance measure");
		profiler.start();
	}

	@Override
	public void stopControllers() {
		System.out.println("PerformanceMeasure is stopping...");
		profiler.interrupt();
	}

	protected void generateTest() throws InterruptedException {
		String testOperatorName = experimentConfig.getOrDefault(TEST_OPERATOR_NAME, "filter");
		int testOpID = findOperatorByName(testOperatorName);
		// 5s
		Thread.sleep(5000);
		loadBalancingAll(testOpID);
		// 100s
		Thread.sleep(95000);
		scaleOutOne(testOpID);
		// 200s
		Thread.sleep(100000);
		scaleOutOne(testOpID);
		// 400s
		Thread.sleep(200000);
		scaleInOne(testOpID);
	}

	private void loadBalancingAll(int testingOpID) throws InterruptedException {
		ExecutionPlan executionPlan = getReconfigurationExecutor().getExecutionPlan();
		scalingByParallelism(testingOpID, executionPlan.getParallelism(testingOpID));
	}

	private void scaleOutOne(int testingOpID) throws InterruptedException {
		ExecutionPlan executionPlan = getReconfigurationExecutor().getExecutionPlan();
		scalingByParallelism(testingOpID, executionPlan.getParallelism(testingOpID) + 1);
	}

	private void scaleInOne(int testingOpID) throws InterruptedException {
		ExecutionPlan executionPlan = getReconfigurationExecutor().getExecutionPlan();
		scalingByParallelism(testingOpID, executionPlan.getParallelism(testingOpID) - 1);
	}

	private void scalingByParallelism(int testingOpID, int newParallelism) throws InterruptedException {
		System.out.println("++++++ start scaling");
		ExecutionPlan executionPlan = getReconfigurationExecutor().getExecutionPlan();
		OperatorDescriptor targetDescriptor = executionPlan.getOperatorByID(testingOpID);


		Map<Integer, List<Integer>> curKeyStateDistribution = targetDescriptor.getKeyStateDistribution();
		int oldParallelism = targetDescriptor.getParallelism();
		assert oldParallelism == curKeyStateDistribution.size() : "old parallelism does not match the key set";

		Map<Integer, List<Integer>> keyStateDistribution = preparePartitionAssignment(newParallelism);

		int maxParallelism = 128;
		for (int i = 0; i < maxParallelism; i++) {
			keyStateDistribution.get(i%newParallelism).add(i);
		}

		// update the parallelism
		targetDescriptor.setParallelism(newParallelism);
//		boolean isScaleIn = oldParallelism > newParallelism;

		// update the key set
		for (OperatorDescriptor parent : targetDescriptor.getParents()) {
			parent.updateKeyMapping(testingOpID, keyStateDistribution);
		}

		if (oldParallelism == newParallelism) {
//			getReconfigurationExecutor().rebalance(executionPlan, testingOpID, this);
			loadBalancing(testingOpID, keyStateDistribution);
		} else {
//			getReconfigurationExecutor().rescale(executionPlan, testingOpID, isScaleIn, this);
			scaling(testingOpID, keyStateDistribution, null);
		}
	}

	private Map<Integer, List<Integer>> preparePartitionAssignment(int parallleism) {
		Map<Integer, List<Integer>> newKeyStateAllocation = new HashMap<>();
		for (int i = 0; i < parallleism; i++) {
			newKeyStateAllocation.put(i, new ArrayList<>());
		}
		return newKeyStateAllocation;
	}

	private class Profiler extends Thread {

		@Override
		public void run() {
			// the testing jobGraph (workload) is in TestingWorkload.java, see that file to know how to use it.
			try {
				Thread.sleep(5000);
				generateTest();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
