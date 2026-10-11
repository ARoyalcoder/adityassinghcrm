package com.pawanputra.bos.audit.internal;

import com.pawanputra.bos.audit.api.AuditEvent;
import com.pawanputra.bos.audit.api.AuditRecorder;
import com.pawanputra.bos.audit.api.AuditedOperation;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;

/** Makes {@link AuditedOperation} work: wraps annotated bean methods and records an event when they return. */
@Configuration
class AuditedOperationConfig {

    private static final ParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor auditedOperationAdvisor(ObjectProvider<AuditRecorder> recorder) {
        MethodInterceptor interceptor = invocation -> {
            Object result = invocation.proceed(); // an exception propagates and nothing is recorded

            Object target = invocation.getThis();
            Method method = target == null
                    ? invocation.getMethod()
                    : AopUtils.getMostSpecificMethod(invocation.getMethod(), target.getClass());
            AuditedOperation operation = AnnotationUtils.findAnnotation(method, AuditedOperation.class);
            if (operation == null) {
                return result;
            }
            AuditEvent event = AuditEvent.of(operation.action(), operation.module(), operation.summary());
            if (!operation.entity().isEmpty()) {
                event.entity(operation.entity(), null, null);
            }
            String[] names = PARAMETER_NAMES.getParameterNames(method);
            Object[] arguments = invocation.getArguments();
            for (int i = 0; i < arguments.length; i++) {
                event.metadata(names != null ? names[i] : "arg" + i, arguments[i]);
            }
            recorder.getObject().record(event);
            return result;
        };
        return new DefaultPointcutAdvisor(
                AnnotationMatchingPointcut.forMethodAnnotation(AuditedOperation.class), interceptor);
    }
}
