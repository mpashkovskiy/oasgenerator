package mpashkovskiy.oasgenerator.jeeexample;

import mpashkovskiy.oasgenerator.core.OasBuilder;
import mpashkovskiy.oasgenerator.core.wrappers.HttpServletResponseCopier;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class OasJeeFilter implements Filter {

    public void init(FilterConfig filterConfig) {}

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (response.getCharacterEncoding() == null) {
            response.setCharacterEncoding("UTF-8");
        }
        HttpServletResponseCopier responseCopier = new HttpServletResponseCopier((HttpServletResponse) response);
        chain.doFilter(request, responseCopier);
        responseCopier.flushBuffer();
        OasBuilder.INSTANCE.add((HttpServletRequest) request, responseCopier);
    }

    public void destroy() {}

}
