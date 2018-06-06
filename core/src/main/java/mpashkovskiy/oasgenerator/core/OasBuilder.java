package mpashkovskiy.oasgenerator.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.models.HttpMethod;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Response;
import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.In;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.util.Json;
import mpashkovskiy.oasgenerator.core.dto.UriMethodOperation;
import mpashkovskiy.oasgenerator.core.utils.HttpStatus;
import mpashkovskiy.oasgenerator.core.wrappers.HttpServletResponseCopier;
import org.apache.commons.io.IOUtils;
import org.jsonschema2pojo.SchemaGenerator;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public enum OasBuilder {
    INSTANCE;

    private ConcurrentLinkedQueue<UriMethodOperation> queue;
    private Swagger specification;

    OasBuilder() {
        specification = new Swagger();
        queue = new ConcurrentLinkedQueue();
        new Thread(() -> {
            while (true) {
                while (!queue.isEmpty())
                    updateSchema(queue.poll());

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    public void add(HttpServletRequest request, HttpServletResponse response) {
        try {
            if (specification.getHost() == null)
                specification
                        .host(request.getServerName() + ":" + request.getServerPort())
                        .scheme(Scheme.forValue(request.getScheme()));

            Operation op = new Operation();
            String uri = makePatternFromURI(request.getRequestURI(), op);
            HttpMethod method = HttpMethod.valueOf(request.getMethod());
            processRequest(request, response, op);
            processResponse(request, response, op);
            queue.add(new UriMethodOperation(uri, method, op));
        } catch (Throwable ex) {}
    }

    private void updateSchema(UriMethodOperation umo) {
        if (specification.getPaths() == null)
            specification.setPaths(new TreeMap<>());

        if (specification.getPaths().containsKey(umo.getUri()) && specification.getPaths().get(umo.getUri()).getOperationMap().containsKey(umo.getMethod()))
            return;

        if (umo.getOp().getSecurity() != null)
            umo.getOp().getSecurity()
                    .stream()
                    .flatMap(s -> s.keySet().stream())
                    .forEach(h -> specification.addSecurityDefinition(h, new ApiKeyAuthDefinition(h, In.HEADER)));

        Path path = new Path();
        path.set(umo.getMethod().toString().toLowerCase(), umo.getOp());
        specification.getPaths().put(umo.getUri(), path);
    }

    private void processRequest(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Operation op) {
        String requestContentType = getContentType(httpServletRequest);
        if (requestContentType.equals("application/json") && httpServletResponse.getStatus() < 300) {
            try {
                String json = IOUtils.toString(httpServletRequest.getReader());
                SchemaGenerator schemaGenerator = new SchemaGenerator();
                ObjectNode schema = schemaGenerator.schemaFromExample(Json.mapper().readTree(json));
                BodyParameter bp = new BodyParameter();
                bp.name("body");
                bp.setRequired(true);
                Model model = Json.mapper().readValue(schema.toString(), ModelImpl.class);
                bp.setSchema(model);
                op.addParameter(bp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        op.addConsumes(requestContentType);
        httpServletRequest
            .getParameterMap()
            .forEach((name, values) -> {
                QueryParameter qp = new QueryParameter();
                qp.name(name);
                if (values != null && values.length != 0) {
                    qp.example(values[0]);
                    qp.type(isInteger(values[0]) ? "integer" : "string");
                }
                op.addParameter(qp);
            });
    }

    private void processResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Operation op) {
        String responseContentType = getContentType(httpServletResponse);
        int statusCode = httpServletResponse.getStatus();
        Response response = new Response();
        response.setDescription(HttpStatus.getByCode(statusCode).name());

        if (responseContentType.equals("application/json")) {
            try {
                byte[] copy = ((HttpServletResponseCopier) httpServletResponse).getCopy();
                String json = new String(copy, "UTF-8");
                SchemaGenerator schemaGenerator = new SchemaGenerator();
                ObjectNode schema = schemaGenerator.schemaFromExample(Json.mapper().readTree(json));
                Model model = Json.mapper().readValue(schema.toString(), ModelImpl.class);
                response.setResponseSchema(model);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        op.addProduces(responseContentType);
        httpServletResponse
                .getHeaderNames()
                .stream()
                .map(String::toLowerCase)
                .filter(h -> h.startsWith("x") || h.equals("authorization"))
                .forEach(h -> op.addSecurity(h, new ArrayList<>()));
        op.addResponse(String.valueOf(statusCode), response);
    }

    private String getContentType(HttpServletRequest request) {
        if (request.getHeader("Content-Type") != null)
            return request.getHeader("Content-Type").split(";")[0];

        if (request.getContentType() != null)
            return request.getContentType().split(";")[0];

        return "application/json";
    }

    private String getContentType(HttpServletResponse response) {
        if (response.getHeader("Content-Type") != null)
            return response.getHeader("Content-Type").split(";")[0];

        if (response.getContentType() != null)
            return response.getContentType().split(";")[0];

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
                pp.type(isInteger(part) ? "integer" : "string");
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

    private static boolean isParameter(String s) {
        if (s.contains(","))
            return true;

        try {
            UUID.fromString(s);
            return true;
        } catch(IllegalArgumentException e) {}

        return isInteger(s);
    }

    private static boolean isInteger(String s) {
        Scanner sc = new Scanner(s.trim());
        if(!sc.hasNextInt(10))
            return false;

        sc.nextInt(10);
        return !sc.hasNext();
    }
}
