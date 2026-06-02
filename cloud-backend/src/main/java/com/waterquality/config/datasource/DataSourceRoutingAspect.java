package com.waterquality.config.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)
@ConditionalOnProperty(name = "spring.datasource.read.enabled", havingValue = "true")
public class DataSourceRoutingAspect {

    private static final Logger log = LoggerFactory.getLogger(DataSourceRoutingAspect.class);

    @Pointcut("execution(* com.waterquality..*.select*(..)) " +
              "|| execution(* com.waterquality..*.get*(..)) " +
              "|| execution(* com.waterquality..*.query*(..)) " +
              "|| execution(* com.waterquality..*.find*(..)) " +
              "|| execution(* com.waterquality..*.list*(..)) " +
              "|| execution(* com.waterquality..*.count*(..))")
    public void readOperations() {}

    @Pointcut("execution(* com.waterquality..*.insert*(..)) " +
              "|| execution(* com.waterquality..*.update*(..)) " +
              "|| execution(* com.waterquality..*.delete*(..)) " +
              "|| execution(* com.waterquality..*.save*(..)) " +
              "|| execution(* com.waterquality..*.remove*(..)) " +
              "|| execution(* com.waterquality..*.batch*(..))")
    public void writeOperations() {}

    @Around("readOperations() && !writeOperations()")
    public Object routeToRead(ProceedingJoinPoint joinPoint) throws Throwable {
        DataSourceType previous = DataSourceContextHolder.get();
        try {
            DataSourceContextHolder.set(DataSourceType.READ_ONLY);
            return joinPoint.proceed();
        } finally {
            if (previous != null) {
                DataSourceContextHolder.set(previous);
            } else {
                DataSourceContextHolder.clear();
            }
        }
    }
}
