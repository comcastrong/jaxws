package com.sun.xml.ws.spi.runtime;

import com.sun.xml.ws.api.WSEndpoint;

/**
 * Root of the SPI implemented by the container
 * (such as application server.)
 *
 * <p>
 * Often technologies that are built on top of JAX-WS
 * (such as Tango) needs to negotiate private contracts between
 * them and the container. This interface allows such technologies
 * to query the negotiated SPI by using the {@link #getSPI(Class)}.
 *
 * <p>
 * For example, if a security pipe needs to get some information
 * from a container, they can do the following:
 * <ol>
 *  <li>Negotiate an interface with the container and define it.
 *      (let's call it <tt>ContainerSecuritySPI</tt>.)
 *  <li>The container will implement <tt>ContainerSecuritySPI</tt>.
 *  <li>At the runtime, a security pipe gets
 *      {@link WSEndpoint} and then to {@link Container}.
 *  <li>It calls <tt>container.getSPI(ContainerSecuritySPI.class)</tt>
 *  <li>The container returns an instance of <tt>ContainerSecuritySPI</tt>.
 *  <li>The security pipe talks to the container through this SPI.
 * </ol>
 *
 * <p>
 * This protects JAX-WS from worrying about the details of such contracts,
 * while still providing the necessary service of hooking up those parties.
 *
 * <p>
 * Technologies that run inside JAX-WS can access this object through
 * {@link WSEndpoint#getContainer()}.
 *
 * @author Kohsuke Kawaguchi
 * @see WSEndpoint
 */
public abstract class Container {
    /**
     * For derived classes.
     */
    protected Container() {
    }

    /**
     * Gets the specified SPI.
     *
     * <p>
     * This method works as a kind of directory service
     * for SPIs between technologies on top of JAX-WS
     * and the container.
     *
     * @param spiType
     *      Always non-null.
     *
     * @return
     *      null if such an SPI is not implemented by this container.
     */
    public abstract <T> T getSPI(Class<T> spiType);
}
