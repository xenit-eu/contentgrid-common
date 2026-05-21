package com.contentgrid.common.spring.autoconfigure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

@ConditionalOnClass(WebMvcConfigurer.class)
public class DisconnectedClientHandlingWebMvcConfigurer implements WebMvcConfigurer {

    @Override
    public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        var iterator = resolvers.listIterator();
        while(iterator.hasNext()) {
            var next = iterator.next();
            if(next instanceof DefaultHandlerExceptionResolver) {
                iterator.set(new CustomDefaultHandlerExceptionResolver());
            }
        }
    }

    private static class CustomDefaultHandlerExceptionResolver extends DefaultHandlerExceptionResolver {

        /**
         * Overwrite the default handling for "disconnected client", because this case is not only hit when a client disconnects before/during the request/response.
         * <p>
         * It can also be triggered by this server disconnecting when talking to upstream services (like database, S3, ...)
         * In that case, we certainly don't want to send a 200 OK status code to our client, as that would indicate that
         * everything is OK.
         * <p>
         * To still handle the case when a client is actually disconnected, catch and silence a potential exception during setting the response status to 500 Internal Server Error
         */
        @Override
        protected ModelAndView handleDisconnectedClientException(Exception ex, HttpServletRequest request,
                HttpServletResponse response, Object handler) {
            try {
                sendServerError(ex, request, response);
            } catch (IOException e) {
                // Swallow error, client connection *might* have been closed, so writing the response may fail
            }
            return super.handleDisconnectedClientException(ex, request, response, handler);
        }
    }
}
