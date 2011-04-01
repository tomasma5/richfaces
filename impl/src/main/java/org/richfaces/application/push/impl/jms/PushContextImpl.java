/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.richfaces.application.push.impl.jms;

import static org.richfaces.application.CoreConfiguration.Items.pushJMSConnectionFactory;
import static org.richfaces.application.CoreConfiguration.Items.pushJMSConnectionPassword;
import static org.richfaces.application.CoreConfiguration.Items.pushJMSConnectionPasswordEnvRef;
import static org.richfaces.application.CoreConfiguration.Items.pushJMSConnectionUsername;
import static org.richfaces.application.CoreConfiguration.Items.pushJMSConnectionUsernameEnvRef;
import static org.richfaces.application.CoreConfiguration.Items.pushJMSTopicsNamespace;
import static org.richfaces.application.CoreConfiguration.PushPropertiesItems.pushPropertiesJMSConnectionFactory;
import static org.richfaces.application.CoreConfiguration.PushPropertiesItems.pushPropertiesJMSConnectionPassword;
import static org.richfaces.application.CoreConfiguration.PushPropertiesItems.pushPropertiesJMSConnectionUsername;
import static org.richfaces.application.CoreConfiguration.PushPropertiesItems.pushPropertiesJMSTopicsNamespace;

import javax.faces.FacesException;
import javax.faces.context.FacesContext;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.PreDestroyApplicationEvent;
import javax.faces.event.SystemEvent;
import javax.faces.event.SystemEventListener;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NameParser;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.atmosphere.cpr.AtmosphereHandler;
import org.richfaces.application.ServiceTracker;
import org.richfaces.application.configuration.ConfigurationService;
import org.richfaces.application.push.PushContext;
import org.richfaces.application.push.SessionFactory;
import org.richfaces.application.push.SessionManager;
import org.richfaces.application.push.TopicsContext;
import org.richfaces.application.push.impl.AtmosphereHandlerProvider;
import org.richfaces.log.Logger;
import org.richfaces.log.RichfacesLogger;

import com.google.common.base.Strings;

/**
 * @author Nick Belaevski
 * 
 */
public class PushContextImpl implements PushContext, SystemEventListener, AtmosphereHandlerProvider {

    private static final Logger LOGGER = RichfacesLogger.APPLICATION.getLogger();

    private MessagingContext messagingContext;

    private TopicsContext topicsContext;

    private PushHandlerImpl pushHandlerImpl;

    public TopicsContext getTopicsContext() {
        return topicsContext;
    }

    private String getApplicationName(FacesContext facesContext) {
        ServletContext servletContext = (ServletContext) facesContext.getExternalContext().getContext();
        return servletContext.getContextPath();
    }

    private String getFirstNonEmptyConfgirutationValue(FacesContext facesContext, ConfigurationService service, Enum<?>... keys) {
        for (Enum<?> key : keys) {
            String value = service.getStringValue(facesContext, key);
            if (!Strings.isNullOrEmpty(value)) {
                return value;
            }
        }
        
        return "";
    }
    
    public void init(FacesContext facesContext) {
        try {
            facesContext.getApplication().subscribeToEvent(PreDestroyApplicationEvent.class, this);
            facesContext.getExternalContext().getApplicationMap().put(PushContext.INSTANCE_KEY_NAME, this);

            ConfigurationService configurationService = ServiceTracker.getService(ConfigurationService.class);
            
            InitialContext initialContext = new InitialContext();
            
            NameParser nameParser = initialContext.getNameParser("");
            
            Name cnfName = nameParser.parse(getConnectionFactory(facesContext, configurationService));
            Name topicsNamespace = nameParser.parse(getTopicsNamespace(facesContext, configurationService));

            messagingContext = new MessagingContext(initialContext, cnfName, topicsNamespace, 
                getApplicationName(facesContext),
                getUserName(facesContext, configurationService),
                getPassword(facesContext, configurationService)
            );

            messagingContext.shareInstance(facesContext);

            messagingContext.start();

            topicsContext = new TopicsContextImpl(messagingContext);

            pushHandlerImpl = new PushHandlerImpl(messagingContext, topicsContext);
        } catch (Exception e) {
            throw new FacesException(e.getMessage(), e);
        }        
    }

    private String getPassword(FacesContext facesContext, ConfigurationService configurationService) {
        return getFirstNonEmptyConfgirutationValue(facesContext, configurationService, 
            pushPropertiesJMSConnectionPassword, pushJMSConnectionPasswordEnvRef, pushJMSConnectionPassword);
    }

    private String getUserName(FacesContext facesContext, ConfigurationService configurationService) {
        return getFirstNonEmptyConfgirutationValue(facesContext, configurationService, 
            pushPropertiesJMSConnectionUsername, pushJMSConnectionUsernameEnvRef, pushJMSConnectionUsername);
    }
    
    private String getConnectionFactory(FacesContext facesContext, ConfigurationService configurationService) {
        return getFirstNonEmptyConfgirutationValue(facesContext, configurationService, 
            pushPropertiesJMSConnectionFactory, pushJMSConnectionFactory);
    }
    
    private String getTopicsNamespace(FacesContext facesContext, ConfigurationService configurationService) {
        return getFirstNonEmptyConfgirutationValue(facesContext, configurationService, 
            pushPropertiesJMSTopicsNamespace, pushJMSTopicsNamespace);
    }

    public void destroy() {
        if (pushHandlerImpl != null) { 
            try {
                pushHandlerImpl.destroy();
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        if (messagingContext != null) {
            try {
                messagingContext.stop();
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    public void processEvent(SystemEvent event) throws AbortProcessingException {
        if (event instanceof PreDestroyApplicationEvent) {
            destroy();
        } else {
            throw new IllegalArgumentException(event.getClass().getName());
        }
    }

    public boolean isListenerForSource(Object source) {
        return true;
    }

    public SessionFactory getSessionFactory() {
        return pushHandlerImpl;
    }

    public AtmosphereHandler<HttpServletRequest, HttpServletResponse> getHandler() {
        return pushHandlerImpl;
    }
    
    public SessionManager getSessionManager() {
        return pushHandlerImpl.getSessionManager();
    }
}