package org.apache.maven.plugins.install;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.maven.api.Project;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Component;
import org.apache.maven.api.plugin.annotations.LifecyclePhase;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ProjectInstaller;
import org.apache.maven.api.services.ProjectInstallerException;
import org.apache.maven.api.services.ProjectInstallerRequest;

/**
 * Installs the project's main artifact, and any other artifacts attached by other plugins in the lifecycle, to the
 * local repository.
 * 
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 */
@Mojo( name = "install", defaultPhase = LifecyclePhase.INSTALL )
public class InstallMojo
    extends AbstractInstallMojo
{

    /**
     * When building with multiple threads, reaching the last project doesn't have to mean that all projects are ready
     * to be installed
     */
    private static final AtomicInteger READYPROJECTSCOUNTER = new AtomicInteger();

    private static final List<ProjectInstallerRequest> INSTALLREQUESTS =
        Collections.synchronizedList( new ArrayList<ProjectInstallerRequest>() );

    /**
     */
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private Project project;

    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    private List<Project> reactorProjects;

    /**
     * Whether every project should be installed during its own install-phase or at the end of the multimodule build. If
     * set to {@code true} and the build fails, none of the reactor projects is installed.
     * <strong>(experimental)</strong>
     * 
     * @since 2.5
     */
    @Parameter( defaultValue = "false", property = "installAtEnd" )
    private boolean installAtEnd;

    /**
     * Set this to <code>true</code> to bypass artifact installation. Use this for artifacts that do not need to be
     * installed in the local repository.
     * 
     * @since 2.4
     */
    @Parameter( property = "maven.install.skip", defaultValue = "false" )
    private boolean skip;

    @Component
    private ProjectInstaller installer;

    public void execute()
        throws MojoException
    {
        boolean addedInstallRequest = false;
        if ( skip )
        {
            logger.info( "Skipping artifact installation" );
        }
        else
        {
            // CHECKSTYLE_OFF: LineLength
            ProjectInstallerRequest projectInstallerRequest =
                ProjectInstallerRequest.builder().project( project )
                        .build();
            // CHECKSTYLE_ON: LineLength

            if ( !installAtEnd )
            {
                installProject( projectInstallerRequest );
            }
            else
            {
                INSTALLREQUESTS.add( projectInstallerRequest );
                addedInstallRequest = true;
            }
        }

        boolean projectsReady = READYPROJECTSCOUNTER.incrementAndGet() == reactorProjects.size();
        if ( projectsReady )
        {
            synchronized ( INSTALLREQUESTS )
            {
                while ( !INSTALLREQUESTS.isEmpty() )
                {
                    installProject( INSTALLREQUESTS.remove( 0 ) );
                }
            }
        }
        else if ( addedInstallRequest )
        {
            logger.info( "Installing " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                + project.getVersion() + " at end" );
        }
    }

    private void installProject( ProjectInstallerRequest pir )
        throws MojoException
    {
        try
        {
            installer.install( pir );
        }
        catch ( ProjectInstallerException e )
        {
            throw new MojoException( "ProjectInstallerException", e );
        }
    }

    public void setSkip( boolean skip )
    {
        this.skip = skip;
    }

}
