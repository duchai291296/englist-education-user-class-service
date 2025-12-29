package com.english.education.aspect;

import com.english.education.annotation.LogAction;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class ActionLoggingAspect {

    @Around("@annotation(logAction)")
    public Object logAction(
            ProceedingJoinPoint jp,
            LogAction logAction
    ) throws Throwable {

        String action = logAction.value();
        String method = jp.getSignature().getDeclaringType().getSimpleName()
                + "." + jp.getSignature().getName();

        log.info("ACTION_START {} ({})", action, method);

        try {
            Object result = jp.proceed();
            log.info("ACTION_END {} ({})", action, method);
            return result;
        } catch (Exception ex) {
            log.error("ACTION_ERROR {} ({})", action, method, ex);
            throw ex;
        }
    }
}
