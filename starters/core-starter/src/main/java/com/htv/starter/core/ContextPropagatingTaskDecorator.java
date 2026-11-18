package com.htv.starter.core;

import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Chép ngữ cảnh (ContextHolder + MDC) của luồng gọi sang luồng chạy tác vụ {@code @Async} /
 * executor, rồi dọn sau khi chạy. Không có lớp này thì mất userId, traceId ở luồng phụ.
 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        RequestContext context = ContextHolder.current().orElse(null);
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            try (ContextHolder.Scope ignored = ContextHolder.open(context)) {
                if (mdc == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(mdc);
                }
                task.run();
            } finally {
                if (previousMdc == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previousMdc);
                }
            }
        };
    }
}
