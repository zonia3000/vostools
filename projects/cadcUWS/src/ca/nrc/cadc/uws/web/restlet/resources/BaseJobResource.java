/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*                                       
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*                                       
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*                                       
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*                                       
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*                                       
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/


package ca.nrc.cadc.uws.web.restlet.resources;

import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.InvalidResourceException;
import ca.nrc.cadc.uws.InvalidServiceException;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.JobRunner;
import ca.nrc.cadc.uws.util.StringUtil;
import ca.nrc.cadc.uws.util.BeanUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.restlet.Client;
import org.restlet.ext.xml.DomRepresentation;
import org.restlet.data.Protocol;
import org.restlet.Response;

import java.io.IOException;
import org.apache.log4j.Logger;
import org.restlet.data.Form;


/**
 * Base Job Resource to obtain Jobs.
 */
public abstract class BaseJobResource extends UWSResource
{
    private static final Logger LOGGER = Logger.getLogger(UWSResource.class);
    
    protected Job job;

    @Override
    protected void doInit()
    {
        super.doInit();
        String jobID = (String) getRequest().getAttributes().get("jobID");
        this.job = getJobManager().getJob(jobID);
        if (job == null)
            throw new InvalidResourceException("No such Job: " + jobID);
        LOGGER.debug("doInit: found job " + jobID);
    }

    /**
     * Obtain whether this job is active.
     *
     * @return  True if active, false otherwise.
     */
    protected boolean jobIsActive()
    {
        final ExecutionPhase executionPhase = job.getExecutionPhase();
        return (executionPhase.equals(ExecutionPhase.QUEUED))
               || (executionPhase.equals(ExecutionPhase.EXECUTING));
    }

    /**
     * Obtain whether this job has successfully completed execution.
     *
     * @return  True if COMPLETE, false otherwise.
     */
    protected boolean jobHasRun()
    {
        final ExecutionPhase executionPhase = job.getExecutionPhase();
        return executionPhase.equals(ExecutionPhase.COMPLETED)
               || executionPhase.equals(ExecutionPhase.ERROR);
    }

    /**
     * Obtain whether this Job is awaiting execution.
     *
     * @return      True if job is waiting, False otherwise.
     */
    protected boolean jobIsPending()
    {
        final ExecutionPhase executionPhase = job.getExecutionPhase();
        return executionPhase.equals(ExecutionPhase.PENDING);
    }

    /**
     * Obtain whether this Job can still have POSTs made to it to modify it.
     *
     * @param form
     * @return  True if it can be modified, False otherwise.
     */
    protected boolean jobModificationAllowed(Form form)
    {
        final String phase =
                form.getFirstValue(JobAttribute.EXECUTION_PHASE.
                        getAttributeName().toUpperCase());

        return jobIsPending() || ((getPathInfo().endsWith("phase")
                                   && StringUtil.hasLength(phase)
                                   && phase.equals("ABORT")));
    }

    /**
     * Obtain a new instance of the Job Runner interface as defined in the
     * Context
     *
     * @return  The JobRunner instance.
     */
    @SuppressWarnings("unchecked")
    protected JobRunner createJobRunner()
    {
        if (!StringUtil.hasText(
                getContext().getParameters().getFirstValue(
                        BeanUtil.UWS_RUNNER)))
        {
            throw new InvalidServiceException(
                    "The JobRunner is mandatory!\n\n Please set the "
                    + BeanUtil.UWS_RUNNER + "context-param in the web.xml, "
                    + "or insert it into the Context manually.");
        }

        final String jobRunnerClassName =
                getContext().getParameters().getFirstValue(BeanUtil.UWS_RUNNER);
        final BeanUtil beanUtil = new BeanUtil(jobRunnerClassName);

        return (JobRunner) beanUtil.createBean();
    }
}
