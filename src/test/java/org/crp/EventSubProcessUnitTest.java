package org.crp;

import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.engine.test.FlowableRule;
import org.flowable.eventsubscription.service.impl.EventSubscriptionQueryImpl;
import org.flowable.task.api.Task;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class EventSubProcessUnitTest {

    @Rule
    public FlowableRule flowableRule = new FlowableRule();

    @Test
    @Deployment(resources = {"org/crp/event-subprocess.bpmn20.xml"})
    public void withoutMessageEvent() {
        ProcessInstance processInstance = flowableRule.getRuntimeService().startProcessInstanceByKey("process");

        // the process instance must have a message event subscription:
        Execution execution = flowableRule.getRuntimeService().createExecutionQuery()
                .processInstanceId(processInstance.getId())
                .messageEventSubscriptionName("newMessage")
                .singleResult();

        assertNotNull(execution);
        assertEquals(1, createEventSubscriptionQuery().count());
        assertEquals(3, flowableRule.getRuntimeService().createExecutionQuery().processInstanceId(processInstance.getId()).count());

        // if we trigger the usertask, the process terminates and the event subscription is removed:
        org.flowable.task.api.Task task = flowableRule.getTaskService().createTaskQuery().singleResult();
        assertEquals("task", task.getTaskDefinitionKey());
        flowableRule.getTaskService().complete(task.getId());
        assertEquals(0, createEventSubscriptionQuery().count());
        assertEquals(0, flowableRule.getRuntimeService().createExecutionQuery().count());
    }

    @Test
    @Deployment(resources = {"org/crp/event-subprocess.bpmn20.xml"})
    public void triggerMessage() {
        // now we start a new instance but this time we trigger the event subprocess:
        ProcessInstance processInstance = flowableRule.getRuntimeService().startProcessInstanceByKey("process");
        Execution execution = flowableRule.getRuntimeService().createExecutionQuery()
                .processInstanceId(processInstance.getId())
                .messageEventSubscriptionName("newMessage")
                .singleResult();

        flowableRule.getRuntimeService().messageEventReceived("newMessage", execution.getId());

        assertEquals(2, flowableRule.getTaskService().createTaskQuery().count());

        // now let's first complete the task in the main flow:
        Task task = flowableRule.getTaskService().createTaskQuery().taskDefinitionKey("task").singleResult();
        flowableRule.getTaskService().complete(task.getId());

        // we still have 3 executions
        assertEquals(3, flowableRule.getRuntimeService().createExecutionQuery().processInstanceId(processInstance.getId()).count());

        // now let's complete the task in the event subprocess
        task = flowableRule.getTaskService().createTaskQuery().taskDefinitionKey("eventSubProcessTask").singleResult();
        // the subprocess task is part of the parent process
        assertThat(task.getProcessInstanceId(), is(processInstance.getId()));
        flowableRule.getTaskService().complete(task.getId());

        // done!
        assertEquals(0, flowableRule.getRuntimeService().createExecutionQuery().processInstanceId(processInstance.getId()).count());
    }


    private EventSubscriptionQueryImpl createEventSubscriptionQuery() {
        return new EventSubscriptionQueryImpl(flowableRule.getProcessEngine().getProcessEngineConfiguration().getCommandExecutor());
    }
}
