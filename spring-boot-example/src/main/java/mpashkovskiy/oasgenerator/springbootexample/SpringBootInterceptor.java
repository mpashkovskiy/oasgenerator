package mpashkovskiy.oasgenerator.springbootexample;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import mpashkovskiy.oasgenerator.core.OasBuilder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

// http://www.mkyong.com/spring-mvc/spring-mvc-handler-interceptors-example/
public class SpringBootInterceptor extends HandlerInterceptorAdapter {

    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws InterruptedException {
        OasBuilder.INSTANCE.add(request, response);
    }

}