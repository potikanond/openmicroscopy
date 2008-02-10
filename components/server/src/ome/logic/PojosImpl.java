/*
 * ome.logic.PojosImpl
 *
 *   Copyright 2006 University of Dundee. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

/*------------------------------------------------------------------------------
 *
 * Written by:    Josh Moore <josh.moore@gmx.de>
 *
 *------------------------------------------------------------------------------
 */

package ome.logic;

// Java imports
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Local;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.interceptor.Interceptors;

import ome.api.IPojos;
import ome.api.ServiceInterface;
import ome.conditions.ApiUsageException;
import ome.conditions.InternalException;
import ome.model.IAnnotated;
import ome.model.ILink;
import ome.model.IObject;
import ome.model.containers.Category;
import ome.model.containers.CategoryGroup;
import ome.model.containers.Dataset;
import ome.model.containers.Project;
import ome.model.core.Image;
import ome.model.meta.Experimenter;
import ome.parameters.Parameters;
import ome.services.query.PojosCGCPathsQueryDefinition;
import ome.services.query.PojosFindAnnotationsQueryDefinition;
import ome.services.query.PojosFindHierarchiesQueryDefinition;
import ome.services.query.PojosGetImagesByOptionsQueryDefinition;
import ome.services.query.PojosGetImagesQueryDefinition;
import ome.services.query.PojosGetUserImagesQueryDefinition;
import ome.services.query.PojosLoadHierarchyQueryDefinition;
import ome.services.query.Query;
import ome.services.util.OmeroAroundInvoke;
import ome.tools.HierarchyTransformations;
import ome.tools.lsid.LsidUtils;
import ome.util.CBlock;
import ome.util.builders.PojoOptions;

import org.jboss.annotation.ejb.LocalBinding;
import org.jboss.annotation.ejb.RemoteBinding;
import org.jboss.annotation.ejb.RemoteBindings;
import org.springframework.transaction.annotation.Transactional;

/**
 * implementation of the Pojos service interface.
 * 
 * @author Josh Moore, <a href="mailto:josh.moore@gmx.de">josh.moore@gmx.de</a>
 * @version 1.0 <small> (<b>Internal version:</b> $Rev$ $Date: 2007-10-03
 *          13:25:20 +0100 (Wed, 03 Oct 2007) $) </small>
 * @since OMERO 2.0
 */
@TransactionManagement(TransactionManagementType.BEAN)
@Transactional
@Stateless
@Remote(IPojos.class)
@RemoteBindings( {
        @RemoteBinding(jndiBinding = "omero/remote/ome.api.IPojos"),
        @RemoteBinding(jndiBinding = "omero/secure/ome.api.IPojos", clientBindUrl = "sslsocket://0.0.0.0:3843") })
@Local(IPojos.class)
@LocalBinding(jndiBinding = "omero/local/ome.api.IPojos")
@Interceptors( { OmeroAroundInvoke.class, SimpleLifecycle.class })
public class PojosImpl extends AbstractLevel2Service implements IPojos {

    public final Class<? extends ServiceInterface> getServiceInterface() {
        return IPojos.class;
    }

    // ~ READ
    // =========================================================================

    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Set loadContainerHierarchy(Class rootNodeType, Set rootNodeIds,
            Map options) {

        PojoOptions po = new PojoOptions(options);

        if (null == rootNodeIds && !po.isExperimenter() && !po.isGroup()) {
            throw new IllegalArgumentException(
                    "Set of ids for loadContainerHierarchy() may not be null "
                            + "if experimenter and group options are null.");
        }

        if (!Project.class.equals(rootNodeType)
                && !Dataset.class.equals(rootNodeType)
                && !CategoryGroup.class.equals(rootNodeType)
                && !Category.class.equals(rootNodeType)) {
            throw new IllegalArgumentException(
                    "Class parameter for loadContainerIHierarchy() must be in "
                            + "{Project,Dataset,Category,CategoryGroup}, not "
                            + rootNodeType);
        }
        // TODO no more "options" just QPs.
        Query<List<IObject>> q = getQueryFactory().lookup(
                PojosLoadHierarchyQueryDefinition.class.getName(),
                new Parameters().addClass(rootNodeType).addIds(rootNodeIds)
                        .addOptions(po.map()));
        List<IObject> l = iQuery.execute(q);
        if (Project.class.equals(rootNodeType)) {
            Set<Dataset> datasets = new HashSet<Dataset>();
            for (IObject o : l) {
                Project p = (Project) o;
                datasets.addAll(p.linkedDatasetList());
            }
            if (datasets.size() > 0) {
                iQuery
                        .findAllByQuery(
                                "select d from Dataset d "
                                        + "left outer join fetch d.annotationLinksCountPerOwner "
                                        + "left outer join fetch d.imageLinksCountPerOwner where d in (:list)",
                                new Parameters().addSet("list", datasets));
            }
        }
        return new HashSet<IObject>(l);

    }

    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Set findContainerHierarchies(final Class rootNodeType,
            final Set imageIds, Map options) {

        PojoOptions po = new PojoOptions(options);

        // TODO refactor to use Hierarchy class H.isTopLevel()
        if (!(Project.class.equals(rootNodeType) || CategoryGroup.class
                .equals(rootNodeType))) {
            throw new ApiUsageException(
                    "Class parameter for findContainerHierarchies() must be"
                            + " in {Project,CategoryGroup}, not "
                            + rootNodeType);
        }

        Query<List<Image>> q = getQueryFactory().lookup(
                PojosFindHierarchiesQueryDefinition.class.getName(),
                new Parameters().addClass(rootNodeType).addIds(imageIds)
                        .addOptions(po.map()));
        List<Image> l = iQuery.execute(q);

        //
        // Destructive changes below this point.
        //
        @SuppressWarnings("unchecked")
        class EvictBlock<E extends IObject> implements CBlock {
            public E call(IObject object) {
                iQuery.evict(object);
                return (E) object;
            };
        }

        // TODO; this if-else statement could be removed if Transformations
        // did their own dispatching
        // TODO: logging, null checking. daos should never return null
        // TODO then size!
        if (Project.class.equals(rootNodeType)) {
            if (imageIds.size() == 0) {
                return new HashSet();
            }

            return HierarchyTransformations.invertPDI(new HashSet<Image>(l),
                    new EvictBlock<IObject>());

        }

        else if (CategoryGroup.class.equals(rootNodeType)) {
            if (imageIds.size() == 0) {
                return new HashSet();
            }

            return HierarchyTransformations.invertCGCI(new HashSet<Image>(l),
                    new EvictBlock<IObject>());
        }

        else {
            throw new InternalException("This can't be reached.");
        }

    }

    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public <T extends IObject, A extends IObject> Map<Long, Set<A>> findAnnotations(
            Class<T> rootNodeType, Set<Long> rootNodeIds,
            Set<Long> annotatorIds, Map options) {

        Map<Long, Set<A>> map = new HashMap<Long, Set<A>>();

        if (rootNodeIds.size() == 0) {
            return map;
        }

        if (!IAnnotated.class.isAssignableFrom(rootNodeType)) {
            throw new IllegalArgumentException(
                    "Class parameter for findAnnotation() "
                            + "must be a subclass of ome.model.IAnnotated");
        }

        PojoOptions po = new PojoOptions(options);

        Query<List<IAnnotated>> q = getQueryFactory().lookup(
                PojosFindAnnotationsQueryDefinition.class.getName(),
                new Parameters().addIds(rootNodeIds).addClass(rootNodeType)
                        .addSet("annotatorIds", annotatorIds).addOptions(
                                po.map()));

        List<IAnnotated> l = iQuery.execute(q);
        // no count collection

        //
        // Destructive changes below this point.
        //
        for (IAnnotated annotated : l) {
            iQuery.evict(annotated);
            annotated.collectAnnotationLinks(new CBlock<ILink>() {

                public ILink call(IObject object) {
                    ILink link = (ILink) object;
                    iQuery.evict(link);
                    iQuery.evict(link.getChild());
                    return null;
                }

            });
        }

        // SORT
        Iterator<IAnnotated> i = new HashSet<IAnnotated>(l).iterator();
        while (i.hasNext()) {
            IAnnotated annotated = i.next();
            Long id = annotated.getId();
            Set<A> set = map.get(id);
            if (set == null) {
                set = new HashSet<A>();
                map.put(id, set);
            }
            set.addAll((List<A>) annotated.linkedAnnotationList());
        }

        return map;

    }

    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Set findCGCPaths(Set imgIds, String algorithm, Map options) {

        if (imgIds.size() == 0) {
            return new HashSet();
        }

        if (!IPojos.ALGORITHMS.contains(algorithm)) {
            throw new IllegalArgumentException("No such algorithm known:"
                    + algorithm);
        }

        PojoOptions po = new PojoOptions(options);

        Query<List<Map<String, IObject>>> q = getQueryFactory().lookup(
                PojosCGCPathsQueryDefinition.class.getName(),
                new Parameters().addIds(imgIds).addAlgorithm(algorithm)
                        .addOptions(po.map()));

        List<Map<String, IObject>> result_set = iQuery.execute(q);

        Map<CategoryGroup, Set<Category>> map = new HashMap<CategoryGroup, Set<Category>>();
        Set<CategoryGroup> returnValues = new HashSet<CategoryGroup>();

        // Parse
        for (Map<String, IObject> entry : result_set) {
            CategoryGroup cg = (CategoryGroup) entry.get(CategoryGroup.class
                    .getName());
            Category c = (Category) entry.get(Category.class.getName());

            if (!map.containsKey(cg)) {
                map.put(cg, new HashSet<Category>());
            }
            if (c != null) {
                map.get(cg).add(c);
            }

        }

        //
        // Destructive changes below this point.
        //
        for (CategoryGroup cg : map.keySet()) {
            iQuery.evict(cg);
            // Overriding various checks.
            // Ticket #92 :
            // We know what we're doing so we place a new HashSet here.
            cg.putAt(CategoryGroup.CATEGORYLINKS, new HashSet());

            for (Category c : map.get(cg)) {
                iQuery.evict(c);
                // Overriding various checks.
                // Ticket #92 again.
                c.putAt(Category.CATEGORYGROUPLINKS, new HashSet());
                cg.linkCategory(c);
            }
            returnValues.add(cg);
        }

        return returnValues;

    }

    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Set getImages(Class rootNodeType, Set rootNodeIds, Map options) {

        if (rootNodeIds.size() == 0) {
            return new HashSet();
        }

        PojoOptions po = new PojoOptions(options);

        Query<List<IObject>> q = getQueryFactory().lookup(
                PojosGetImagesQueryDefinition.class.getName(),
                new Parameters().addIds(rootNodeIds).addClass(rootNodeType)
                        .addOptions(po.map()));

        List<IObject> l = iQuery.execute(q);
        return new HashSet<IObject>(l);

    }

    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Set getImagesByOptions(Map options) {

        PojoOptions po = new PojoOptions(options);

        if (!po.isStartTime() && !po.isEndTime()) {
            throw new IllegalArgumentException("start or end time option "
                    + "is required for getImagesByOptions().");
        }

        Query<List<IObject>> q = getQueryFactory().lookup(
                PojosGetImagesByOptionsQueryDefinition.class.getName(),
                new Parameters().addOptions(options));

        List<IObject> l = iQuery.execute(q);
        return new HashSet<IObject>(l);

    }

    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Set getUserImages(Map options) {

        PojoOptions po = new PojoOptions(options);

        if (!po.isExperimenter() && !po.isGroup()) {
            throw new IllegalArgumentException("experimenter or group option "
                    + "is required for getUserImages().");
        }

        Query<List<Image>> q = getQueryFactory().lookup(
                PojosGetUserImagesQueryDefinition.class.getName(),
                new Parameters().addOptions(options));

        List<Image> l = iQuery.execute(q);
        return new HashSet<Image>(l);

    }

    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Map getUserDetails(Set names, Map options) {

        List results;
        Map<String, Experimenter> map = new HashMap<String, Experimenter>();

        /* query only if we have some ids */
        if (names.size() > 0) {
            Parameters params = new Parameters().addSet("name_list", names);
            results = iQuery.findAllByQuery("select e from Experimenter e "
                    + "left outer join fetch e.groupExperimenterMap gs "
                    + "left outer join fetch gs.parent g "
                    + "where e.omeName in ( :name_list )", params);

            for (Object object : results) {
                Experimenter e = (Experimenter) object;
                map.put(e.getOmeName(), e);
            }
        }

        /* ensures all ids appear in map */
        for (Object object : names) {
            String name = (String) object;
            if (!map.containsKey(name)) {
                map.put(name, null);
            }
        }

        return map;

    }

    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Map getCollectionCount(String type, String property, Set ids,
            Map options) {

        String parsedProperty = LsidUtils.parseField(property);

        checkType(type);
        checkProperty(type, parsedProperty);

        Map<Long, Integer> results = new HashMap<Long, Integer>();

        String query = "select size(table." + parsedProperty + ") from " + type
                + " table where table.id = :id";
        // FIXME: optimize by doing new list(id,size(table.property)) ... group
        // by id
        for (Iterator iter = ids.iterator(); iter.hasNext();) {
            Long id = (Long) iter.next();
            Query<List<Integer>> q = getQueryFactory().lookup(query,
                    new Parameters().addId(id));
            Integer count = iQuery.execute(q).get(0);
            results.put(id, count);
        }

        return results;
    }

    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Collection retrieveCollection(IObject arg0, String arg1, Map arg2) {
        IObject context = iQuery.get(arg0.getClass(), arg0.getId());
        Collection c = (Collection) context.retrieve(arg1); // FIXME not
        // type.o.null safe
        iQuery.initialize(c);
        return c;
    }

    // ~ WRITE
    // =========================================================================

    @RolesAllowed("user")
    @Transactional(readOnly = false)
    public IObject createDataObject(IObject arg0, Map arg1) {
        IObject retVal = iUpdate.saveAndReturnObject(arg0);
        return retVal;
    }

    @RolesAllowed("user")
    @Transactional(readOnly = false)
    public IObject[] createDataObjects(IObject[] arg0, Map arg1) {
        IObject[] retVal = iUpdate.saveAndReturnArray(arg0);
        return retVal;

    }

    @RolesAllowed("user")
    @Transactional(readOnly = false)
    public void unlink(ILink[] arg0, Map arg1) {
        deleteDataObjects(arg0, arg1);
    }

    @RolesAllowed("user")
    @Transactional(readOnly = false)
    public ILink[] link(ILink[] arg0, Map arg1) {
        IObject[] retVal = iUpdate.saveAndReturnArray(arg0);
        // IUpdate returns an IObject array here. Can't be cast using (Link[])
        ILink[] links = new ILink[retVal.length];
        System.arraycopy(retVal, 0, links, 0, retVal.length);
        return links;

    }

    @RolesAllowed("user")
    @Transactional(readOnly = false)
    public IObject updateDataObject(IObject arg0, Map arg1) {
        IObject retVal = iUpdate.saveAndReturnObject(arg0);
        return retVal;

    }

    @RolesAllowed("user")
    @Transactional(readOnly = false)
    public IObject[] updateDataObjects(IObject[] arg0, Map arg1) {
        IObject[] retVal = iUpdate.saveAndReturnArray(arg0);
        return retVal;
    }

    @RolesAllowed("user")
    @Transactional(readOnly = false)
    public void deleteDataObject(IObject row, Map arg1) {
        iUpdate.deleteObject(row);
    }

    @RolesAllowed("user")
    @Transactional(readOnly = false)
    public void deleteDataObjects(IObject[] rows, Map options) {
        for (IObject object : rows) {
            deleteDataObject(object, options);
        }

    }

    // ~ Helpers
    // =========================================================================

    final static String alphaNumeric = "^\\w+$";

    final static String alphaNumericDotted = "^\\w[.\\w]+$"; // TODO

    // annotations

    protected void checkType(String type) {
        if (!type.matches(alphaNumericDotted)) {
            throw new IllegalArgumentException(
                    "Type argument to getCollectionCount may ONLY be "
                            + "alpha-numeric with dots (" + alphaNumericDotted
                            + ")");
        }

        if (!iQuery.checkType(type)) {
            throw new IllegalArgumentException(type + " is an unknown type.");
        }
    }

    protected void checkProperty(String type, String property) {

        if (!property.matches(alphaNumeric)) {
            throw new IllegalArgumentException("Property argument to "
                    + "getCollectionCount may ONLY be alpha-numeric ("
                    + alphaNumeric + ")");
        }

        if (!iQuery.checkProperty(type, property)) {
            throw new IllegalArgumentException(type + "." + property
                    + " is an unknown property on type " + type);
        }

    }

}
