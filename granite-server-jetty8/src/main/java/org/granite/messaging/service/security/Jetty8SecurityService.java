/**
 *   GRANITE DATA SERVICES
 *   Copyright (C) 2006-2014 GRANITE DATA SERVICES S.A.S.
 *
 *   This file is part of the Granite Data Services Platform.
 *
 *   Granite Data Services is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Lesser General Public
 *   License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or (at your option) any later version.
 *
 *   Granite Data Services is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 *   General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the Free Software
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 *   USA, or see <http://www.gnu.org/licenses/>.
 */
package org.granite.messaging.service.security;

import java.lang.reflect.InvocationTargetException;
import java.security.Principal;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.UserIdentity;
import org.granite.context.GraniteContext;
import org.granite.messaging.webapp.HttpGraniteContext;
import org.granite.messaging.webapp.ServletGraniteContext;


/**
 * @author William DRAI
 */
public class Jetty8SecurityService extends AbstractSecurityService {


    public void configure(Map<String, String> params) {
    }


    @Override
    public void prelogin(HttpSession session, Object httpRequest, String servletName) {
        if (session == null) // Cannot prelogin() without a session
            return;

        if (session.getAttribute(AuthenticationContext.class.getName()) instanceof Jetty8AuthenticationContext)
            return;

        Request request = (Request)httpRequest;
        Authentication authentication = request.getAuthentication();
        UserIdentity.Scope scope = request.getUserIdentityScope();

        Jetty8AuthenticationContext authorizationContext = new Jetty8AuthenticationContext(scope, authentication);
        session.setAttribute(AuthenticationContext.class.getName(), authorizationContext);
    }


    public Principal login(Object credentials, String charset) throws SecurityServiceException {
        String[] decoded = decodeBase64Credentials(credentials, charset);

        ServletGraniteContext graniteContext = (ServletGraniteContext)GraniteContext.getCurrentInstance();
        Principal principal = null;

        if (graniteContext instanceof HttpGraniteContext) {
            HttpServletRequest httpRequest = graniteContext.getRequest();
            Request request = (Request)httpRequest;
            Authentication authentication = request.getAuthentication();
            UserIdentity.Scope scope = request.getUserIdentityScope();

            Jetty8AuthenticationContext authenticationContext = new Jetty8AuthenticationContext(scope, authentication);
            principal = authenticationContext.authenticate(decoded[0], decoded[1]);
            if (principal != null)
                graniteContext.getSession().setAttribute(AuthenticationContext.class.getName(), authenticationContext);
        }
        else {
            AuthenticationContext authenticationContext = (AuthenticationContext)graniteContext.getSession().getAttribute(AuthenticationContext.class.getName());
            if (authenticationContext != null)
                principal = authenticationContext.authenticate(decoded[0], decoded[1]);
        }

        if (principal == null)
            throw SecurityServiceException.newInvalidCredentialsException("Wrong username or password");

        endLogin(credentials, charset);

        return principal;
    }


    public Object authorize(AbstractSecurityContext context) throws Exception {

        startAuthorization(context);

        ServletGraniteContext graniteContext = (ServletGraniteContext)GraniteContext.getCurrentInstance();
        HttpServletRequest httpRequest = null;
        AuthenticationContext authenticationContext = null;
        Principal principal = null;

        if (graniteContext instanceof HttpGraniteContext) {
            httpRequest = graniteContext.getRequest();
            HttpSession session = httpRequest.getSession(false);

            if (session != null) {
                authenticationContext = (AuthenticationContext)session.getAttribute(AuthenticationContext.class.getName());
                if (authenticationContext != null) {
                    principal = authenticationContext.getPrincipal();
                    ((Request)httpRequest).setAuthentication(((Jetty8AuthenticationContext)authenticationContext).getAuthentication());
                }

                if (principal == null && tryRelogin()) {
                    Request request = (Request)httpRequest;
                    Authentication authentication = request.getAuthentication();
                    if (authentication instanceof Authentication.User)
                        principal = ((Authentication.User)authentication).getUserIdentity().getUserPrincipal();
                }
            }
        }
        else {
            HttpSession session = graniteContext.getSession(false);
            if (session != null) {
                authenticationContext = (AuthenticationContext)session.getAttribute(AuthenticationContext.class.getName());
                if (authenticationContext != null)
                    principal = authenticationContext.getPrincipal();
            }
        }

        if (context.getDestination().isSecured()) {
            if (principal == null) {
                if (httpRequest != null && httpRequest.getRequestedSessionId() != null) {
                    HttpSession httpSession = httpRequest.getSession(false);
                    if (httpSession == null || !httpRequest.getRequestedSessionId().equals(httpSession.getId()))
                        throw SecurityServiceException.newSessionExpiredException("Session expired");
                }
                throw SecurityServiceException.newNotLoggedInException("User not logged in");
            }

            if (httpRequest == null && authenticationContext == null)
                throw SecurityServiceException.newNotLoggedInException("No authorization context");

            boolean accessDenied = true;
            for (String role : context.getDestination().getRoles()) {
                if (httpRequest != null && httpRequest.isUserInRole(role)) {
                    accessDenied = false;
                    break;
                }
                if (authenticationContext != null && authenticationContext.isUserInRole(role)) {
                    accessDenied = false;
                    break;
                }
            }
            if (accessDenied)
                throw SecurityServiceException.newAccessDeniedException("User not in required role");
        }

        try {
            return endAuthorization(context);
        } catch (InvocationTargetException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof SecurityException)
                    throw SecurityServiceException.newAccessDeniedException(t.getMessage());
            }
            throw e;
        }
    }


    public void logout() throws SecurityServiceException {
        ServletGraniteContext graniteContext = (ServletGraniteContext)GraniteContext.getCurrentInstance();
        if (graniteContext instanceof HttpGraniteContext) {
            Request request = (Request)graniteContext.getRequest();
            Authentication authentication = request.getAuthentication();
            if (authentication instanceof Authentication.User)
                ((Authentication.User)authentication).logout();

            if (request.getSession(false) != null) {
                endLogout();
                
                try {
                	request.getSession(false).invalidate();
                }
                catch (IllegalStateException e) {
                	// Session already invalid
                }
            }
        }
        else {
            HttpSession session = graniteContext.getSession();
            if (session != null) {
                AuthenticationContext authenticationContext = (AuthenticationContext)session.getAttribute(AuthenticationContext.class.getName());
                authenticationContext.logout();

                try {
                	session.removeAttribute(AuthenticationContext.class.getName());
                }
                catch (IllegalStateException e) {
                	// Session already invalid
                }
                
                endLogout();
                
                try {
                	session.invalidate();
                }
                catch (IllegalStateException e) {
                	// Session already invalid
                }
            }
        }
    }


    public static class Jetty8AuthenticationContext implements AuthenticationContext {
    	
		private static final long serialVersionUID = 1L;
		
		private transient final UserIdentity.Scope scope;
        private transient Authentication authentication;
        private transient Principal principal;
        
        public Jetty8AuthenticationContext(UserIdentity.Scope scope, Authentication authentication) {
            this.scope = scope;
            this.authentication = authentication;
        }
        
        public Principal authenticate(String username, String password) {
        	if (authentication == null)
        		throw SecurityServiceException.newAuthenticationFailedException("Invalid authentication");
        	
            if (authentication instanceof Authentication.Deferred)
                authentication = ((Authentication.Deferred)authentication).login(username, password, ((ServletGraniteContext)GraniteContext.getCurrentInstance()).getRequest());

            if (authentication instanceof Authentication.User)
                principal = ((Authentication.User)authentication).getUserIdentity().getUserPrincipal();

            return principal;
        }
        
        public boolean isValid() {
        	return principal != null;
        }
        
        public Principal getPrincipal() {
            return principal;
        }

        public Authentication getAuthentication() {
            return authentication;
        }

        public boolean isUserInRole(String role) {
            if (authentication instanceof Authentication.User)
                return ((Authentication.User)authentication).isUserInRole(scope, role);
            return false;
        }

        public void logout() {
            if (authentication instanceof Authentication.User)
                ((Authentication.User)authentication).logout();
        }
    }
}