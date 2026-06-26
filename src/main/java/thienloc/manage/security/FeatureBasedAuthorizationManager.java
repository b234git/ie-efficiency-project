package thienloc.manage.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import thienloc.manage.service.PermissionService;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Authorization manager that maps each request URL+method to a feature via
 * {@link PermissionService} and grants access only when the caller's role
 * holds that feature. URLs not claimed by any feature fall back to "any
 * authenticated user is allowed" so /home and similar landing pages still
 * work without explicit configuration.
 */
@Component
@RequiredArgsConstructor
public class FeatureBasedAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final PermissionService permissionService;

    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authenticationSupplier,
                                           RequestAuthorizationContext context) {
        Authentication auth = authenticationSupplier.get();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return new AuthorizationDecision(false);
        }

        HttpServletRequest req = context.getRequest();
        String path = req.getServletPath();
        if (path == null || path.isEmpty()) {
            path = req.getRequestURI();
        }
        String method = req.getMethod();

        Optional<String> featureKey = permissionService.findFeatureKey(path, method);
        if (featureKey.isEmpty()) {
            // No feature claims this URL → any authenticated user may access.
            return new AuthorizationDecision(true);
        }

        String username = auth.getName();
        if (username == null || username.isBlank()) return new AuthorizationDecision(false);

        return new AuthorizationDecision(permissionService.userHasFeature(username, featureKey.get()));
    }
}
