/*
 * org.openmicroscopy.shoola.env.data.OMEDSGateway
 *
 *------------------------------------------------------------------------------
 *
 *  Copyright (C) 2004 Open Microscopy Environment
 *      Massachusetts Institute of Technology,
 *      National Institutes of Health,
 *      University of Dundee
 *
 *
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public
 *    License along with this library; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *------------------------------------------------------------------------------
 */

package org.openmicroscopy.shoola.env.data;

//Java imports
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.ds.Criteria;
import org.openmicroscopy.ds.DataFactory;
import org.openmicroscopy.ds.DataServer;
import org.openmicroscopy.ds.DataServices;
import org.openmicroscopy.ds.RemoteAuthenticationException;
import org.openmicroscopy.ds.RemoteCaller;
import org.openmicroscopy.ds.RemoteConnectionException;
import org.openmicroscopy.ds.RemoteServerErrorException;
import org.openmicroscopy.ds.ServerVersion;
import org.openmicroscopy.ds.dto.Attribute;
import org.openmicroscopy.ds.dto.DataInterface;
import org.openmicroscopy.ds.dto.Dataset;
import org.openmicroscopy.ds.dto.UserState;
import org.openmicroscopy.ds.managers.AnnotationManager;
import org.openmicroscopy.ds.managers.DatasetManager;
import org.openmicroscopy.ds.managers.ProjectManager;
import org.openmicroscopy.ds.managers.RemoteImportManager;
import org.openmicroscopy.ds.st.Experimenter;
import org.openmicroscopy.ds.st.Repository;
import org.openmicroscopy.is.ImageServerException;
import org.openmicroscopy.is.PixelsFactory;

/** 
 * Unified access point to the various <i>OMEDS</i> services.
 *
 * @author  Jean-Marie Burel &nbsp;&nbsp;&nbsp;&nbsp;
 * 				<a href="mailto:j.burel@dundee.ac.uk">j.burel@dundee.ac.uk</a>
 * @author  <br>Andrea Falconi &nbsp;&nbsp;&nbsp;&nbsp;
 * 				<a href="mailto:a.falconi@dundee.ac.uk">
 * 					a.falconi@dundee.ac.uk</a>
 * @version 2.2
 * <small>
 * (<b>Internal version:</b> $Revision$ $Date$)
 * </small>
 * @since OME2.2
 */
class OMEDSGateway 
{

    private static final int    MAJOR_OMEDS_VERSION = 2, 
                                MINOR_OMEDS_VERSION = 2,
                                PATCH_OMEDS_VERSION = 1;
    
    private static final String MESSAGE = "The version of OMEDS must be 2.2.1";               
	/**
	 * The factory provided by the connection library to access the various
	 * <i>OMEDS</i> services.
	 */
	private DataServices	proxiesFactory;
	
	/**
	 * Tells whether we're currently connected and logged into <i>OMEDS</i>.
	 */
	private boolean			connected;
		
	/**
	 * Helper method to handle exceptions thrown by the connection library.
	 * Methods in this class are required to fill in a meaningful context
	 * message which will be usd in the case of a 
	 * {@link RemoteServerErrorException}.
	 * This method is not supposed to be used in this class' constructor or in
	 * the login/logout methods.
	 *  
	 * @param e		The exception.
	 * @param contextMessage	The context message.	
	 * @throws DSOutOfServiceException	A connection problem.
	 * @throws DSAccessException	A server-side error.
	 */
	private void handleException(Exception e, String contextMessage) 
		throws DSOutOfServiceException, DSAccessException
	{
		if (e instanceof RemoteConnectionException) {
			connected = false;
			throw new DSOutOfServiceException("Can't connect to OMEDS.", e);
		} else if (e instanceof RemoteAuthenticationException) {
			connected = false;
			throw new DSOutOfServiceException("Failed to log in.", e);
		} else if (e instanceof RemoteServerErrorException) {
			throw new DSAccessException(contextMessage, e);
		} else if (e instanceof ImageServerException) {
			//tempo, throw by pixelsFactory.
			throw new DSAccessException(contextMessage, e);
		} else {
			//This should never be reached.  If so, there's a bug in the 
			//connection library.
			logout();
			connected = false;
			throw new RuntimeException("Internal error.", e);
		}
	}
	
	/**
	 * Utility method to print the contents of a list in a string.
	 * 
	 * @param l		The list.
	 * @return	See above.
	 */
	private String printList(List l) 
	{
		StringBuffer buf = new StringBuffer();
		if (l == null)	buf.append("<null> list");
		else if (l.size() == 0)		buf.append("empty list");
		else {
			Iterator i = l.iterator();
			while (i.hasNext()) {
				buf.append(i.next());
				buf.append(" ");
			}
		}
		return buf.toString();
	}
	
	/**
	 * 
	 * @param omedsAddress
	 * @throws DSOutOfServiceException	if the URL is not valid.
	 */
	OMEDSGateway(URL omedsAddress) 
		throws DSOutOfServiceException
	{
		try {
			proxiesFactory = DataServer.getDefaultServices(omedsAddress);
            serverVersionCheck();
		} catch (Exception e) {
			String s = "Can't connect to OMEDS. URL not valid.";
			throw new DSOutOfServiceException(s, e);
		}
	}
    
	/**
	 * Tries to connect to <i>OMEDS</i> and log in by using the supplied
	 * credentials.
	 * 
	 * @param userName	The user name to be used for login.
	 * @param password	The password to be used for login.
	 * @throws DSOutOfServiceException If the connection can't be established
	 * 									or the credentials are invalid.
	 */
	void login(String userName, String password)
		throws DSOutOfServiceException
	{
		try {
			RemoteCaller proxy = proxiesFactory.getRemoteCaller();
			proxy.login(userName, password);
			connected = true;
		} catch (RemoteConnectionException rce) {
			throw new DSOutOfServiceException("Can't connect to OMEDS.", rce);
		} catch (RemoteAuthenticationException rae) {
			throw new DSOutOfServiceException("Failed to log in.", rae);
		}
	}
	
	void logout()
	{
		RemoteCaller proxy = proxiesFactory.getRemoteCaller();
		connected = false;
		proxy.logout();
		
		//TODO: The proxy should throw a checked exception on failure!
		//Catch that exception when the connection lib will be modified.
		
		//TODO: The proxy should have a dispose method to release resources
		//like sockets.  Add this call when connection lib will be modified.
		//proxy.dispose();	
		
	}
	
	/**
	 * Tells whether the communication channel to <i>OMEDS</i> is currently
	 * connected.
	 * This means that we have established a connection and have sucessfully
	 * logged in.
	 * 
	 * @return	<code>true</code> if connected, <code>false</code> otherwise.
	 */
	boolean isConnected() { return connected; }
	
	DataFactory getDataFactory()
	{
		return (DataFactory) proxiesFactory.getService(DataFactory.class);
	}
	
	private ProjectManager getProjectManager()
	{
		return (ProjectManager) proxiesFactory.getService(ProjectManager.class);
	}
	
	private DatasetManager getDatasetManager()
	{
		return (DatasetManager) proxiesFactory.getService(DatasetManager.class);
	}
	
	private AnnotationManager getAnnotationManager()
	{
		return (AnnotationManager) proxiesFactory.getService(
													AnnotationManager.class);
	}
	
	private PixelsFactory getPixelsFactory()
	{
		return (PixelsFactory) proxiesFactory.getService(PixelsFactory.class);
	}
	
	private RemoteImportManager getRemoteImportManager()
	{
		return (RemoteImportManager) proxiesFactory.getService(
										RemoteImportManager.class);

	}
	/** Retrieve the current experimenter. */
	Experimenter getCurrentUser(Criteria c)
		throws DSOutOfServiceException, DSAccessException
	{
		UserState us = null;
		try {
			us = getDataFactory().getUserState(c);
		} catch (Exception e) {
			handleException(e, "Can't retrieve the user state.");
		} 
		return us.getExperimenter();
	}
	
	/**
	 * Create a new Data Interface object
	 * Wrap the call to the {@link DataFactory#createNew(Class) create}
	 * method.
	 * @param dto 	targetClass, the core data type to count.
	 * @return DataInterface.
	 * @throws DSOutOfServiceException If the connection is broken, or logged in
	 * @throws DSAccessException If an error occured while trying to 
	 * create a DataInterface from OMEDS service. 
	 */
	DataInterface createNewData(Class dto)
			throws DSOutOfServiceException, DSAccessException 
	{
		DataInterface retVal = null;
		try {
			retVal = getDataFactory().createNew(dto);
		} catch (Exception e) {
			handleException(e, "Can't create DataInterface: "+dto+".");
		} 
		return retVal; 
	}

	/**
	 * Create a new Attribute object
	 * Wrap the call to the {@link DataFactory#createNew(String) create}
	 * method.
	 * @param semanticType 	the semantic type to create.
	 * @return Attribute.
	 * @throws DSOutOfServiceException If the connection is broken, or logged in
	 * @throws DSAccessException If an error occured while trying to 
	 * create a DataInterface from OMEDS service. 
	 */
	Attribute createNewData(String semanticTypeName) 
		throws DSOutOfServiceException, DSAccessException 
	{
		Attribute retVal = null;
		try {
			retVal = getDataFactory().createNew(semanticTypeName);
		} catch (Exception e) {
			handleException(e, "Can't create Attribute: "+semanticTypeName+".");
		} 
		return retVal; 
	}
    
	/**
	 * Retrieve the graph defined by the criteria.
	 * Wrap the call to the 
	 * {@link DataFactory#retrieveList(Class, int, Criteria) retrieve}
	 * method.
	 *  
	 * @param dto		targetClass, the core data type to count.
	 * @param c			criteria by which the object graph is pulled out.
	 * @return
	 * @throws DSOutOfServiceException If the connection is broken, or logged in
	 * @throws DSAccessException If an error occured while trying to 
	 * retrieve data from OMEDS service. 
	 */
	Object retrieveListData(Class dto, Criteria c) 
		throws DSOutOfServiceException, DSAccessException
	{
		Object retVal = null;
		try {
			retVal = getDataFactory().retrieveList(dto, c);
		} catch (Exception e) {
			handleException(e, "Can't retrieve list. Type: "+dto+", Criteria: "+
								c+".");
		}
		return retVal;
	}
	
	/**
	 * Load the graph defined by the criteria.
	 * Wrap the call to the 
	 * {@link DataFactory#retrieve(Class, Criteria) retrieve} method.
	 * 
	 * @param dto		targetClass, the core data type to count.
	 * @param c			criteria by which the object graph is pulled out. 
	 * @throws DSOutOfServiceException If the connection is broken, or logged in
	 * @throws DSAccessException If an error occured while trying to 
	 * retrieve data from OMEDS service.  
	 */
	Object retrieveData(Class dto, Criteria c) 
		throws DSOutOfServiceException, DSAccessException 
   	{
	   	Object retVal = null;
	   	try {
		   	retVal = getDataFactory().retrieve(dto, c);
		} catch (Exception e) {
			handleException(e, "Can't retrieve object. Type: "+dto+
								", Criteria: "+c+".");
		}
		return retVal;
	}
   	
   	/**
   	 * Load the graph defined by the criteria.
	 * Wrap the call to the 
	 * {@link DataFactory#retrieveList(String, Criteria) retrieve} method.
	 * 
   	 * @param semanticTypeName	specified semanticType.
   	 * @param c					criteria by which the object graph is pulled 
   	 * 							out.
   	 * @throws DSOutOfServiceException If the connection is broken, or logged in
	 * @throws DSAccessException If an error occured while trying to 
	 * retrieve data from OMEDS service. 
   	 */
   	Object retrieveListSTSData(String semanticTypeName, Criteria c)
		throws DSOutOfServiceException, DSAccessException 
	{
		Object retVal = null;
		try {
			retVal = getDataFactory().retrieveList(semanticTypeName, c);
		} catch (Exception e) {
			handleException(e, "Can't retrieve list. Type: "+
								semanticTypeName+", Criteria: "+c+".");
		}
		return retVal;
	}
   	
   	/**
   	 * Load the graph defined by the criteria.
	 * Wrap the call to the 
	 * {@link DataFactory#retrieve(String, Criteria) retrieve} method.
   	 * @param semanticTypeName	specified semanticType.
   	 * @param c					criteria by which the object graph is pulled 
   	 * 							out.
   	 * @return
   	 * @throws DSOutOfServiceException
   	 * @throws DSAccessException
   	 */
   	Object retrieveSTSData(String semanticTypeName, Criteria c)
		throws DSOutOfServiceException, DSAccessException
	{
		Object retVal = null;
		try {
			retVal = getDataFactory().retrieve(semanticTypeName, c);
		} catch (Exception e) {
			handleException(e, "Can't retrieve object. Type: "+
								semanticTypeName+", Criteria: "+c+".");
		}
		return retVal;
	}
   		
	/**
	 * Wrap the call to the 
	 * {@link DataFactory#count(SemanticType, Criteria) count}
	 * method.
	 * @param type		type, the semantic type to count.
	 * @param c			criteria by which the object graph is pulled out.
	 * @return
	 * @throws DSOutOfServiceException If the connection is broken, or logged in
	 * @throws DSAccessException If an error occured while trying to 
	 * retrieve data from OMEDS service. 
	 */
	int countData(String type, Criteria c)
		throws DSOutOfServiceException, DSAccessException
	{
		int val = -1;
		try {
			val = getDataFactory().count(type, c);
		} catch (Exception e) {
			handleException(e, "Can't count objects. Type: "+type+
								", Criteria: "+c+".");
		}
		return val;
	}
	
	/** Mark the specified dataInterface for update. */
	void markForUpdate(DataInterface object) 
	{
		getDataFactory().markForUpdate(object);
	}
   
   	/** Commit the marked objects. */
	void updateMarkedData()
		throws DSOutOfServiceException, DSAccessException 
	{
		try {
			getDataFactory().updateMarked();
		} catch (Exception e) {
			handleException(e, "Can't update the marked data.");
		}
	}
	
	/** Add a list of {@link Dataset}s to a {@link Project}. */
	void addDatasetsToProject(int projectID, List datasetIDs)
		throws DSOutOfServiceException, DSAccessException
	{
		try {
			getProjectManager().addDatasetsToProject(projectID, datasetIDs);
		} catch (Exception e) {
			handleException(e, "Can't add datasets to project "+projectID+"."+
								"(Datasets: "+printList(datasetIDs)+".)");
		}
	}
	
	/** Add a {@link Dataset} to a list of {@link Project}s. */
	void addDatasetToProjects(int datasetID, List projectIDs)
		throws DSOutOfServiceException, DSAccessException
	{
		try {
			getProjectManager().addDatasetToProjects(projectIDs, datasetID);
		} catch (Exception e) {
			handleException(e, "Can't add dataset "+datasetID+" to projects."+
								"(Projects: "+printList(projectIDs)+".)");
		}
	}
	
	/** Remove a list of {@link Dataset}s from a {@link Project}.  */
	void removeDatasetsFromProject(int projectID, List datasetsIDs)
		throws DSOutOfServiceException, DSAccessException
	{
		try {
	  		getProjectManager().removeDatasetsFromProject(projectID, 
	  														datasetsIDs);
		} catch (Exception e) {
			handleException(e, "Can't remove datasets from project "+projectID+
								"(Datasets: "+printList(datasetsIDs)+".)");
		}
	}
	
	/** Add a list of {@link Image}s to a {@link Dataset}. */
	void addImagesToDataset(int datasetID, List imageIDs)
		throws DSOutOfServiceException, DSAccessException
	{
		try {
			getDatasetManager().addImagesToDataset(datasetID, imageIDs);
		} catch (Exception e) {
			handleException(e, "Can't add images to dataset "+datasetID+
								"(Images: "+printList(imageIDs)+".)");
		}
	}
	
	/** Remove a list of {@link Image}s from a {@link Dataset}.  */
	void removeImagesFromDataset(int datasetID, List imagesIDs)
		throws DSOutOfServiceException, DSAccessException
	{
		try {
			getDatasetManager().removeImagesFromDataset(datasetID, imagesIDs);
		} catch (Exception e) {
			handleException(e, "Can't remove images from dataset "+datasetID+
								"(Images: "+printList(imagesIDs)+".)");
		} 
	}
	
	/** 
	 * Annotate. Each attribute in the list must be a newly-created
     * attribute; otherwise, call updateAttributes() with that attribute
     * as a member.
     */
	void annotateAttributesData(List attributes)
		throws DSOutOfServiceException, DSAccessException
	{
		try {
            getAnnotationManager().annotateAttributes(attributes);
		} catch (Exception e) {
			handleException(e, "Can't annotate attributes ("+
								printList(attributes)+").");
		}  
	}
    
    /** Update attributes. */
    void updateAttributes(List attributes)
        throws DSOutOfServiceException, DSAccessException
    {
        try {
            getDataFactory().updateList(attributes);
		} catch (Exception e) {
			handleException(e, "Can't update attributes ("+
								printList(attributes)+").");
		}
    }
    
    /** Return the repository where to store the files. */
	Repository getRepository()
		throws DSOutOfServiceException
	{
		Repository repository = null;
		try {
			repository = getPixelsFactory().findRepository(0);
		} catch (ImageServerException ise) {
			throw new DSOutOfServiceException("Can't connect to OMEIS.", ise);
		}
		return repository;
	}
	
	/** Upload the specified file. */
	Long uploadFile(Repository rep, File file)
		throws DSOutOfServiceException, DSAccessException
	{
		Long ID = null;
		try {
			ID = new Long(getPixelsFactory().uploadFile(rep, file));
		} catch (ImageServerException ise) {
			throw new DSOutOfServiceException("Can't connect to OMEIS.", ise);
		} catch(FileNotFoundException fnfe) {
			throw new DSAccessException("Can't retrieve the file "+file, fnfe);
		}
		return ID;
	}
	
	/** Start the import. */
	void startImport(Dataset dataset, List filesID)
	{
		getRemoteImportManager().startRemoteImport(dataset, filesID);
	}
	
    /** Check the version of OMEDS installed. */
    private void serverVersionCheck()
        throws DSOutOfServiceException
    {
        try {
            RemoteCaller proxy = proxiesFactory.getRemoteCaller();
            ServerVersion sv = proxy.getServerVersion();
            if (sv.getMajorVersion() != MAJOR_OMEDS_VERSION)
                throw new DSOutOfServiceException(MESSAGE);
            if (sv.getMinorVersion() != MINOR_OMEDS_VERSION)
                throw new DSOutOfServiceException(MESSAGE);
            if (sv.getPatchVersion() != PATCH_OMEDS_VERSION)
                throw new DSOutOfServiceException(MESSAGE);
        } catch (Exception e) {
            String s = "Can't connect to OMEDS";
            throw new DSOutOfServiceException(s, e);
        }
    }
    
}

