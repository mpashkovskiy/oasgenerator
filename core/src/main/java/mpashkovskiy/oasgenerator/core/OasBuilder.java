package mpashkovskiy.oasgenerator.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.models.*;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.util.Json;
import org.apache.commons.io.IOUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public enum OasBuilder {
    INSTANCE;

//    private BlockingQueue<RequestResponse> queue;
    private Swagger specification;

    OasBuilder() {
        specification = new Swagger();
//        queue = new ArrayBlockingQueue(10, true);
//        Runnable runnable = () -> {
//            while (true) {
//                try {
//                    updateSchema(queue.take());
//                } catch (InterruptedException ex) {
//                    break;
//                }
//            }
//        };
//        runnable.run();
    }

    public void add(HttpServletRequest request, HttpServletResponse response) {
//        queue.put(new RequestResponse(request, response));
        updateSchema(new RequestResponse(request, response));
    }

    private void updateSchema(RequestResponse rr) {
        if (specification.getPaths() == null)
            specification.setPaths(new HashMap<>());

        Operation op = new Operation();
        String uri = makePatternFromURI(rr.getRequest().getRequestURI(), op);
        HttpMethod method = HttpMethod.valueOf(rr.getRequest().getMethod());
//        if (specification.getPaths().containsKey(uri) && specification.getPaths().get(uri).getOperationMap().containsKey(method))
//            return;

        if (specification.getHost() == null) {
            specification.setHost(rr.getRequest().getServerName() + ":" + rr.getRequest().getServerPort());
            specification.addScheme(Scheme.forValue(rr.getRequest().getScheme()));
        }
        processRequest(rr, op);
        processResponse(rr, op);
        addPath(op, uri, method);
    }

    private void addPath(Operation op, String uri, HttpMethod method) {
        Path path = new Path();
        path.set(method.toString().toLowerCase(), op);
        specification.getPaths().put(uri, path);
    }

    private void processResponse(RequestResponse rr, Operation op) {
        String responseContentType = getContentType(rr.getResponse());
        if (responseContentType.equals("application/json")) {
            try {
                byte[] copy = ((HttpServletResponseCopier) rr.getResponse()).getCopy();
                String json = new String(copy, "UTF-8");
                System.out.println(json);
            } catch (UnsupportedEncodingException e) {}
        }
        op.addProduces(responseContentType);
        rr.getResponse()
            .getHeaderNames()
            .stream()
            .map(String::toLowerCase)
            .filter(h -> h.startsWith("x") || h.equals("authorization"))
            .forEach(h -> op.addSecurity(h, new ArrayList<>()));
        op.addResponse(String.valueOf(rr.getResponse().getStatus()), new Response());
    }

    private void processRequest(RequestResponse rr, Operation op) {
        String requestContentType = getContentType(rr.getRequest());
        if (requestContentType.equals("application/json")) {
            try {
                String json = IOUtils.toString(rr.getRequest().getReader());
                System.out.println(json);
            } catch (IOException e) {
                e.printStackTrace();
            }
            ((ResettableStreamHttpServletRequest) rr.getRequest()).resetInputStream();
        }
        op.addConsumes(requestContentType);
        rr.getRequest()
            .getParameterMap()
            .forEach((name, values) -> {
                QueryParameter qp = new QueryParameter();
                qp.name(name);
                if (values != null && values.length != 0)
                    qp.example(values[0]);
                op.addParameter(qp);
            });
    }

    private String getContentType(HttpServletRequest request) {
        if (request.getHeader("Content-Type") != null) {
            return request.getHeader("Content-Type").split(";")[0];
        }
        if (request.getContentType() != null) {
            return request.getContentType().split(";")[0];
        }
        return "application/json";
    }

    private String getContentType(HttpServletResponse response) {
        if (response.getHeader("Content-Type") != null) {
            return response.getHeader("Content-Type").split(";")[0];
        }
        if (response.getContentType() != null) {
            return response.getContentType().split(";")[0];
        }
        return "application/json";
    }

    private String makePatternFromURI(String uri, Operation op) {
        String prevPart = null;
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String newPart = part;
            if (prevPart != null && !prevPart.trim().equals("") && isInteger(part, 10)) {
                String paramName = prevPart + "Id";
                PathParameter pp = new PathParameter();
                pp.name(paramName);
                pp.example(part);
                op.addParameter(pp);
                newPart = "{" + paramName + "}";
            }
            prevPart = part;
            parts[i] = newPart;
        }
        return String.join("/", parts);
    }

    public String getSpecificationJson() throws JsonProcessingException {
        return Json.mapper().writeValueAsString(specification);
    }

    public static boolean isInteger(String s, int radix) {
        Scanner sc = new Scanner(s.trim());
        if(!sc.hasNextInt(radix)) return false;
        sc.nextInt(radix);
        return !sc.hasNext();
    }
}
