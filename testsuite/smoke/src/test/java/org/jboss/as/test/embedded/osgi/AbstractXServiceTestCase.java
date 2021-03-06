/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.jboss.as.test.embedded.osgi;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jboss.as.osgi.service.BundleContextService;
import org.jboss.as.osgi.service.BundleManagerService;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartException;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.osgi.framework.Bundle;

/**
 * Abstract base for XService testing.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 14-Oct-2010
 */
abstract class AbstractXServiceTestCase {

    abstract ServiceContainer getServiceContainer();

    long registerModule(ModuleIdentifier moduleId) throws Exception {
        final ServiceController<?> bundleContextService = getServiceContainer().getRequiredService(BundleContextService.SERVICE_NAME);
        if (bundleContextService.getMode() == Mode.ON_DEMAND && bundleContextService.getState() == State.DOWN) {
            final CountDownLatch latch = new CountDownLatch(1);
            bundleContextService.addListener(new AbstractServiceListener<Object>() {

                @Override
                public void serviceStarted(ServiceController<? extends Object> controller) {
                    latch.countDown();
                    controller.removeListener(this);
                }

                @Override
                public void serviceFailed(ServiceController<? extends Object> controller, StartException reason) {
                    latch.countDown();
                    controller.removeListener(this);
                }
            });
            bundleContextService.setMode(Mode.ACTIVE);
            latch.await(10, TimeUnit.SECONDS);

            if (bundleContextService.getState() != State.UP)
                throw new IllegalStateException("BundleContextService not started");
        }

        final ServiceController<?> bundleManagerService = getServiceContainer().getRequiredService(BundleManagerService.SERVICE_NAME);
        BundleManager bundleManager = (BundleManager) bundleManagerService.getValue();
        if (bundleManager == null)
            throw new IllegalStateException("BundleManagerService not started");

        Bundle bundle = bundleManager.installBundle(moduleId);
        return bundle.getBundleId();
    }

    void assertServiceState(ServiceName serviceName, State expState, long timeout) throws Exception {
        ServiceController<?> controller = getServiceContainer().getService(serviceName);
        State state = controller != null ? controller.getState() : null;
        while ((state == null || state != expState) && timeout > 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                // ignore
            }
            controller = getServiceContainer().getService(serviceName);
            state = controller != null ? controller.getState() : null;
            timeout -= 100;
        }
        assertEquals(serviceName.toString(), expState, state);
    }
}
