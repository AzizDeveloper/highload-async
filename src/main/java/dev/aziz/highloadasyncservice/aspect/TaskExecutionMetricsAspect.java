package dev.aziz.highloadasyncservice.aspect;

import dev.aziz.highloadasyncservice.observability.metrics.TaskMetrics;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
public class TaskExecutionMetricsAspect {

    private final TaskMetrics taskMetrics;

    @Around("execution(* dev.aziz.highloadasyncservice.service.TaskStatusService.executeTask(..))")
    public Object measureExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            taskMetrics.recordSuccess();
            return result;
        } catch (RuntimeException e) {
            taskMetrics.recordFailure();
            throw e;
        } finally {
            taskMetrics.recordDuration(System.nanoTime() - start);
        }
    }
}
