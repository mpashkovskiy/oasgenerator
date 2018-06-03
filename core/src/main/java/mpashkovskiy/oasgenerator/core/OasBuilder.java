package mpashkovskiy.oasgenerator.core;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.models.*;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.In;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.util.Json;
import mpashkovskiy.oasgenerator.core.utils.HttpStatus;
import mpashkovskiy.oasgenerator.core.wrappers.HttpServletResponseCopier;
import mpashkovskiy.oasgenerator.core.wrappers.ResettableStreamHttpServletRequest;
import org.apache.commons.io.IOUtils;
import org.jsonschema2pojo.SchemaGenerator;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
            specification.setPaths(new TreeMap<>());

        Operation op = new Operation();
        String uri = makePatternFromURI(rr.getRequest().getRequestURI(), op);
        HttpMethod method = HttpMethod.valueOf(rr.getRequest().getMethod());
        if (specification.getPaths().containsKey(uri) && specification.getPaths().get(uri).getOperationMap().containsKey(method))
            return;

        if (specification.getHost() == null) {
            specification
                .host(rr.getRequest().getServerName() + ":" + rr.getRequest().getServerPort())
                .scheme(Scheme.forValue(rr.getRequest().getScheme()));
        }
        processRequest(rr, op);
        processResponse(rr, op);
        addPath(uri, method, op);
    }

    private void addPath(String uri, HttpMethod method, Operation op) {
        Path path = new Path();
        path.set(method.toString().toLowerCase(), op);
        specification.getPaths().put(uri, path);
    }

    private void processResponse(RequestResponse rr, Operation op) {
        String responseContentType = getContentType(rr.getResponse());
        int statusCode = rr.getResponse().getStatus();
        Response response = new Response();
        response.setDescription(HttpStatus.getByCode(statusCode).name());

        if (responseContentType.equals("application/json")) {
            try {
                byte[] copy = ((HttpServletResponseCopier) rr.getResponse()).getCopy();
                String json = new String(copy, "UTF-8");
                ObjectNode schema = new SchemaGenerator().schemaFromExample(Json.mapper().readTree(json));
                Model model = Json.mapper().readValue(schema.toString(), ModelImpl.class);
                response.setResponseSchema(model);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (JsonParseException e) {
                e.printStackTrace();
            } catch (JsonMappingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        op.addProduces(responseContentType);
        rr.getResponse()
            .getHeaderNames()
            .stream()
            .map(String::toLowerCase)
            .filter(h -> h.startsWith("x") || h.equals("authorization"))
            .forEach(h -> {
                op.addSecurity(h, new ArrayList<>());
                specification.addSecurityDefinition(h, new ApiKeyAuthDefinition(h, In.HEADER));
            });
        op.addResponse(String.valueOf(statusCode), response);
    }

    private void processRequest(RequestResponse rr, Operation op) {
        String requestContentType = getContentType(rr.getRequest());
        if (requestContentType.equals("application/json")) {
            try {
                String json = IOUtils.toString(rr.getRequest().getReader());
                ObjectNode schema = new SchemaGenerator().schemaFromExample(Json.mapper().readTree(json));
                BodyParameter bp = new BodyParameter();
                bp.name("body");
                bp.setRequired(true);
                Model model = Json.mapper().readValue(schema.toString(), ModelImpl.class);
                bp.setSchema(model);
                op.addParameter(bp);
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
            String part = parts[i].trim();
            String newPart = part;
            if (prevPart != null && prevPart.length() > 0 && isParameter(part)) {
                String paramName = new String(prevPart);
                if (paramName.endsWith("ies")) {
                    paramName = paramName.substring(0, paramName.length() - 3) + "y";
                } else if (paramName.endsWith("s")) {
                    paramName = paramName.substring(0, paramName.length() - 1);
                }
                paramName += "Id";
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

    public static boolean isParameter(String s) {
        if (s.contains(","))
            return true;

        try {
            UUID.fromString(s);
            return true;
        } catch(IllegalArgumentException e) {}

        // is int?
        Scanner sc = new Scanner(s.trim());
        if(!sc.hasNextInt(10))
            return false;

        sc.nextInt(10);
        return !sc.hasNext();
    }
}
