/*
 * org.openmicroscopy.shoola.env.data.views.calls.ClassificationLoader
 *
 *------------------------------------------------------------------------------
 *  Copyright (C) 2006 University of Dundee. All rights reserved.
 *
 *
 * 	This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */

package org.openmicroscopy.shoola.env.data.views.calls;


//Java imports
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.env.data.OmeroDataService;
import org.openmicroscopy.shoola.env.data.views.BatchCall;
import org.openmicroscopy.shoola.env.data.views.BatchCallTree;

import pojos.CategoryData;
import pojos.CategoryGroupData;

/** 
 * Command to find the Category Group/Category paths that end or don't end with
 * a specified Image.
 * <p>This command can be created to load either all the Categories under which
 * a given Image was classified and all enclosing Category Groups or to do the
 * opposite &#151; to load all the Categories the given Image doesn't belong in,
 * and then all the Category Groups that contain those Categories.</p>
 * <p>The object returned in the <code>DSCallOutcomeEvent</code> will be a
 * <code>Set</code> with all Category Group nodes (as <code>CategoryGroupData
 * </code> objects) that were found.  Those objects will also be linked to the 
 * matching Categories (represented by <code>CategoryData</code> objects).</p>
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
public class ClassificationLoader
    extends BatchCallTree
{
    
	/** Indicates to retrieve all the tags. */
	public static final int ALL_TAGS = 100;
	
	/** Indicates to retrieve the tags and tag sets linked to the images. */
	public static final int TAGS_USED = 101;
	
	/** Indicates to retrieve the tags not linked to the images. */
	public static final int TAGS_AVAILABLE = 102;
	
    /** Identifies the <code>Declassification</code> algorithm. */
    public static final int DECLASSIFICATION = 
                                OmeroDataService.DECLASSIFICATION;
    
    /**
     * Identifies the <code>Classification</code> algorithm with
     * mutually exclusive rule.
     */
    public static final int CLASSIFICATION_ME = 
                                OmeroDataService.CLASSIFICATION_ME;
    
    /**
     * Identifies the <code>Classification</code> algorithm without
     * mutually exclusive rule.
     */
    public static final int CLASSIFICATION_NME = 
                            	OmeroDataService.CLASSIFICATION_NME;
    
    /** The root nodes of the found trees. */
    private Object		rootNodes;
    
    /** Searches the CG/C/I hierarchy. */
    private BatchCall   loadCall;
     
    /**
     * Checks if the index specified is supported.
     * 
     * @param i The passed index.
     * @return 	Returns <code>true</code> if the index is supported,
     * 			<code>false</code> otherwise.
     */
    private boolean checkAlgorithmIndex(int i)
    {
        switch (i) {
            case DECLASSIFICATION:
            case CLASSIFICATION_ME:
            case CLASSIFICATION_NME: 
            case ALL_TAGS:
            case TAGS_USED:
            case TAGS_AVAILABLE:
                return true;
            default: return false;
        }
    }
    
    /**
     * Creates a {@link BatchCall} to load all Category Group/Category paths
     * that don't end with the specified Image.
     * If bad arguments are passed, we throw a runtime
	 * exception so to fail early and in the caller's thread.
     * 
     * @param imageIDs      The set of image ids.
     * @param algorithm     One out of the following constants:
     *                      {@link #DECLASSIFICATION},
     *                      {@link #CLASSIFICATION_ME},
     *                      {@link #CLASSIFICATION_NME}.
     * @param userID   		The Id of the user.                  
     * @return The {@link BatchCall}.
     */
    private BatchCall loadCGCPaths(final Set imageIDs, final int algorithm, 
            final long userID)
    {
        return new BatchCall("Loading CGC paths. ") {
            public void doCall() throws Exception
            {
                OmeroDataService os = context.getDataService();
                if (algorithm == CLASSIFICATION_NME)
                    rootNodes = os.loadContainerHierarchy(
                            CategoryGroupData.class, null, false, userID);
                else {
                	rootNodes = os.findCategoryPaths(imageIDs, false, userID);
                	//rootNodes = os.findCGCPaths(imageIDs, algorithm, userID);
                }
                    
            }
        };
    }
  
    /**
     * Creates a {@link BatchCall} to load all tags, the one linked to
     * the images and the available ones.
     * If bad arguments are passed, we throw a runtime
	 * exception so to fail early and in the caller's thread.
     * 
     * @param imageIDs	The id of the images.
     * @param userID	The Id of the user.                  
     * @return The {@link BatchCall}.
     */
    private BatchCall loadAllTags(final Set<Long> imageIDs, 
    										final long userID)
    {
        return new BatchCall("Loading all tags. ") {
            public void doCall() throws Exception
            {
                OmeroDataService os = context.getDataService();
                List<CategoryData> r = new ArrayList<CategoryData>();
                Set nodes;
                Iterator i;
                CategoryData category;
                List<Long> ids = new ArrayList<Long>();
                if (imageIDs != null) {
                	nodes = os.findCategoryPaths(imageIDs, false, userID);
                    i = nodes.iterator();
                    while (i.hasNext()) {
                    	category = (CategoryData) i.next();
             			if (!ids.contains(category.getId())) {
             				r.add(category);
             				ids.add(category.getId());
             			}
    	         	}
                }
                

	         	List<List> results = new ArrayList<List>(3);
	         	results.add(r);
	         	nodes = os.loadContainerHierarchy(
                        CategoryData.class, null, false, userID);
	         	i = nodes.iterator();
	         	r = new ArrayList<CategoryData>();
	         	while (i.hasNext()) {
	         		category = (CategoryData) i.next();
	         		if (!ids.contains(category.getId())) {
         				r.add(category);
         			}
				}
	         	results.add(r);
	         	
	         	nodes = os.loadTopContainerHierarchy(
                        CategoryGroupData.class, userID);
	         	i = nodes.iterator();
	         	List rg = new ArrayList();
	         	while (i.hasNext()) {
	         		rg.add(i.next());
				}
	         	results.add(rg);
	         	rootNodes = results;
            }
        };
    }
    
    /**
     * Creates a {@link BatchCall} to load all tags linked to the images.
     * If bad arguments are passed, we throw a runtime
	 * exception so to fail early and in the caller's thread.
     * 
     * @param imageIDs	The id of the images.
     * @param userID	The Id of the user.                  
     * @return The {@link BatchCall}.
     */
    private BatchCall loadUnlinkedTags(final Set<Long> imageIDs, 
    								final long userID)
    {
        return new BatchCall("Loading linked tags. ") {
            public void doCall() throws Exception
            {
            	OmeroDataService os = context.getDataService();
                List<CategoryData> r = new ArrayList<CategoryData>();
                Set nodes;
                Iterator i;
                CategoryData category;
                List<Long> ids = new ArrayList<Long>();
                if (imageIDs != null) {
                	nodes = os.findCategoryPaths(imageIDs, false, userID);
                    i = nodes.iterator();
                    while (i.hasNext()) {
                    	category = (CategoryData) i.next();
             			if (!ids.contains(category.getId())) {
             				r.add(category);
             				ids.add(category.getId());
             			}
    	         	}
                }
                nodes = os.loadContainerHierarchy(
                        CategoryData.class, null, false, userID);
	         	i = nodes.iterator();
	         	r = new ArrayList<CategoryData>();
	         	while (i.hasNext()) {
	         		category = (CategoryData) i.next();
	         		if (!ids.contains(category.getId())) {
         				r.add(category);
         			}
				}
	         	
	         	rootNodes = r;
            }
        };
    }
    
    /**
     * Creates a {@link BatchCall} to load all tags not linked to the images.
     * If bad arguments are passed, we throw a runtime
	 * exception so to fail early and in the caller's thread.
     * 
     * @param imageIDs	The id of the images.
     * @param userID	The Id of the user.                  
     * @return The {@link BatchCall}.
     */
    private BatchCall loadLinkedTags(final Set<Long> imageIDs, 
    								final long userID)
    {
        return new BatchCall("Loading unlinked tags. ") {
            public void doCall() throws Exception
            {
            	OmeroDataService os = context.getDataService();
                List<CategoryData> r = new ArrayList<CategoryData>();
                Set nodes;
                Iterator i;
                CategoryData category;
                List<Long> ids = new ArrayList<Long>();
                if (imageIDs != null) {
                	nodes = os.findCategoryPaths(imageIDs, false, userID);
                    i = nodes.iterator();
                    while (i.hasNext()) {
                    	category = (CategoryData) i.next();
             			if (!ids.contains(category.getId())) {
             				r.add(category);
             				ids.add(category.getId());
             			}
    	         	}
                }
                

	         	List<List> results = new ArrayList<List>(2);
	         	results.add(r);
	        
	         	nodes = os.findCGCPaths(imageIDs, 
	         				OmeroDataService.DECLASSIFICATION, userID);
	         	i = nodes.iterator();
	         	List rg = new ArrayList();
	         	while (i.hasNext()) {
	         		rg.add(i.next());
				}
	         	results.add(rg);
	         	rootNodes = results;
            }
        };
    }
    
    /**
     * Creates a {@link BatchCall} to load all Categories containing the image.
     * If bad arguments are passed, we throw a runtime
	 * exception so to fail early and in the caller's thread.
     * 
     * @param imagesID	The id of the images.
     * @param leaves	Passed <code>true</code> to retrieve the images
     * 					<code>false</code> otherwise.
     * @param userID	The Id of the user.                  
     * @return The {@link BatchCall}.
     */
    private BatchCall loadPartialClassification(final Set<Long> imagesID, 
    										final boolean leaves,
    										final long userID)
    {
        return new BatchCall("Loading CGC paths. ") {
            public void doCall() throws Exception
            {
                OmeroDataService os = context.getDataService();
                rootNodes = os.findCategoryPaths(imagesID, leaves, userID);
            }
        };
    }
    
    /**
     * Adds the {@link #loadCall} to the computation tree.
     * @see BatchCallTree#buildTree()
     */
    protected void buildTree() { add(loadCall); }

    /**
     * Returns, in a <code>Set</code>, the root nodes of the found trees.
     * @see BatchCallTree#getResult()
     */
    protected Object getResult() { return rootNodes; }
    
    /**
     * Creates a new instance to find the Category Group/Category paths that 
     * end or don't end with the specified Image.
     * If bad arguments are passed, we throw a runtime exception so to fail
     * early and in the caller's thread.
     * 
     * @param imageID       The id of the Image to classify or declassifiy
     *                      depending on the algorithm.
     * @param leaves		Passed <code>true</code> to retrieve the images
     * 						<code>false</code> otherwise.
     * @param userID   		The Id of the user.                    
     */
    public ClassificationLoader(long imageID, boolean leaves, long userID)
    {
        if (imageID < 0) 
            throw new IllegalArgumentException("image ID not valid ");
        Set<Long> images = new HashSet<Long>(1);
        images.add(imageID);
        loadCall = loadLinkedTags(images, userID);
    }
    
    /**
     * Creates a new instance to find the Category Group/Category paths that 
     * end or don't end with the specified Image.
     * If bad arguments are passed, we throw a runtime exception so to fail
     * early and in the caller's thread.
     * 
     * @param imagesID       The id of the Image to classify or declassifiy
     *                      depending on the algorithm.
     * @param leaves		Passed <code>true</code> to retrieve the images
     * 						<code>false</code> otherwise.
     * @param userID   		The Id of the user.                    
     */
    public ClassificationLoader(Set<Long> imagesID, boolean leaves, long userID)
    {
        if (imagesID == null || imagesID.size() == 0) 
            throw new IllegalArgumentException("image ID not valid ");
        loadCall = loadLinkedTags(imagesID, userID);
    }
    
    /**
     * Creates a new instance to find the Category Group/Category paths that 
     * end or don't end with the specified Image.
     * If bad arguments are passed, we throw a runtime exception so to fail
     * early and in the caller's thread.
     * 
     * @param imageID       The id of the Image to classify or declassifiy
     *                      depending on the algorithm.
     * @param algorithm     One out of the following constants:
     *                      {@link #ALL_TAGS} or {@link #TAGS_USED}.
     * @param userID   		The Id of the user.                    
     */
    public ClassificationLoader(long imageID, int algorithm, long userID)
    {
        if (imageID < 0) 
            throw new IllegalArgumentException("image ID not valid ");
        if (!checkAlgorithmIndex(algorithm))
            throw new IllegalArgumentException("Algorithm not supported.");
        Set<Long> images = new HashSet<Long>(1);
        images.add(imageID);
        switch (algorithm) {
			case ALL_TAGS:
				loadCall = loadAllTags(images, userID);
				break;
			case TAGS_USED:
				loadCall = loadLinkedTags(images, userID);
				break;
			case TAGS_AVAILABLE:
				loadCall = loadUnlinkedTags(images, userID);
				break;
			default:
	        	loadCall  = loadCGCPaths(images, algorithm, userID);
		}
    }
   
    /**
     * Creates a new instance to find the Category Group/Category paths that 
     * end or don't end with the specified Image.
     * If bad arguments are passed, we throw a runtime exception so to fail
     * early and in the caller's thread.
     * 
     * @param imageIDs      The collection of image's ids.
     * @param algorithm     One of the constants defined by this class.
     * @param userID   		The Id of the user.                
     */
    public ClassificationLoader(Set imageIDs, int algorithm, long userID)
    {
        if ((imageIDs == null || imageIDs.size() == 0) && 
        		algorithm == DECLASSIFICATION)
            throw new IllegalArgumentException("The collection of ids" +
                    "cannot be null or of size 0.");
        if (!checkAlgorithmIndex(algorithm))
            throw new IllegalArgumentException("Algorithm not supported.");
        switch (algorithm) {
			case ALL_TAGS:
				loadCall = loadAllTags(imageIDs, userID);
				break;
			case TAGS_USED:
				loadCall = loadLinkedTags(imageIDs, userID);
				break;
			case TAGS_AVAILABLE:
				loadCall = loadUnlinkedTags(imageIDs, userID);
				break;
			default:
	        	loadCall  = loadCGCPaths(imageIDs, algorithm, userID);
        }
        //loadCall  = loadCGCPaths(imageIDs, algorithm, userID);
    }
    
}
