package thienloc.manage.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String ATTR_PREFIX = "rate_limit_";

    // path-prefix → cooldown in milliseconds
    private final Map<String, Long> rules;

    public RateLimitInterceptor(Map<String, Long> rules) {
        this.rules = new ConcurrentHashMap<>(rules);
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String uri = request.getRequestURI();
        Long cooldownMs = matchCooldown(uri);
        if (cooldownMs == null) return true;

        HttpSession session = request.getSession(true);
        String attrKey = ATTR_PREFIX + uri;

        Long lastCall = (Long) session.getAttribute(attrKey);
        long now = System.currentTimeMillis();

        if (lastCall != null && (now - lastCall) < cooldownMs) {
            long remainSec = (cooldownMs - (now - lastCall)) / 1000 + 1;

            String referer = request.getHeader("Referer");
            String redirect = (referer != null && !referer.isBlank()) ? referer : "/";

            // Dùng Spring FlashMap để importError xuất hiện tự động trong model sau redirect
            FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
            if (flash != null) {
                flash.put("importError",
                        "Vui lòng chờ " + remainSec + " giây trước khi thử lại.");
            }

            response.sendRedirect(redirect);
            return false;
        }

        session.setAttribute(attrKey, now);
        return true;
    }

    private Long matchCooldown(String uri) {
        for (Map.Entry<String, Long> entry : rules.entrySet()) {
            if (uri.startsWith(entry.getKey())) return entry.getValue();
        }
        return null;
    }
}
