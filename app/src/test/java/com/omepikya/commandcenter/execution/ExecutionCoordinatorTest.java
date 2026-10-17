package com.omepikya.commandcenter.execution;

import com.omepikya.commandcenter.core.CommandContext;
import com.omepikya.commandcenter.core.CommandResult;
import com.omepikya.commandcenter.core.CommandType;
import com.omepikya.commandcenter.router.Action;
import com.omepikya.commandcenter.router.ActionRegistry;
import com.omepikya.commandcenter.router.ActionRouter;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExecutionCoordinatorTest {
    @Test public void routesCommandThroughExecutorAndCoordinator() {
        ActionRegistry registry=new ActionRegistry();
        registry.register(new Action(){
            @Override public String getName(){return "test-action";}
            @Override public boolean canHandle(CommandContext context){return "run test action".equalsIgnoreCase(context.getRawCommand());}
            @Override public CommandResult execute(CommandContext context){return CommandResult.success("action completed");}
        });
        ActionExecutor executor=new ActionExecutor(new ActionRouter(registry));
        ExecutionCoordinator coordinator=new ExecutionCoordinator(executor,new RecoveryEngine());
        CommandContext context=new CommandContext("run test action",CommandType.DEVICE_ACTION);
        ExecutionResult result=coordinator.execute(context);
        assertTrue(result.isSuccess());
        assertEquals("action completed",result.getMessage());
        assertEquals(ExecutionStatus.SUCCESS,coordinator.getLastTrace().getState());
        assertEquals(1,executor.getHistory().size());
        assertTrue(coordinator.getEventBus().getRecentEvents(10).size()>0);
    }
    @Test public void returnsCancelledResultWhenTokenWasCancelledBeforeStart() {
        ActionExecutor executor = new ActionExecutor(
                new ActionRouter(new ActionRegistry()));
        ExecutionCoordinator coordinator = new ExecutionCoordinator(
                executor, new RecoveryEngine());
        ExecutionCancellation cancellation = new ExecutionCancellation();
        cancellation.cancel("Cancelled before start.");

        ExecutionResult result = coordinator.execute(
                new CommandContext("unknown command", CommandType.DEVICE_ACTION),
                cancellation);

        assertTrue(!result.isSuccess());
        assertEquals(ExecutionStatus.CANCELLED, result.getStatus());
        assertEquals(ExecutionStatus.CANCELLED,
                coordinator.getLastTrace().getState());
        assertEquals(0, executor.getHistory().size());
    }

    @Test public void rejectsMissingActionWithoutUnsafeRetry() {
        ActionExecutor executor=new ActionExecutor(new ActionRouter(new ActionRegistry()));
        ExecutionCoordinator coordinator=new ExecutionCoordinator(executor,new RecoveryEngine());
        ExecutionResult result=coordinator.execute(new CommandContext("unknown command",CommandType.DEVICE_ACTION));
        assertTrue(!result.isSuccess());
        assertEquals(ExecutionStatus.FAILED,coordinator.getLastTrace().getState());
        assertEquals(1,executor.getHistory().size());
    }
}