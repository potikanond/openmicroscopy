/*
 *   $Id$
 *
 *   Copyright 2010 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.services.delete;

import java.util.List;

import ome.api.IDelete;
import ome.services.graphs.AbstractStepFactory;
import ome.services.graphs.GraphEntry;
import ome.services.graphs.GraphException;
import ome.services.graphs.GraphSpec;
import ome.services.graphs.GraphStep;
import ome.system.OmeroContext;
import ome.tools.hibernate.ExtendedMetadata;
import ome.tools.hibernate.QueryBuilder;
import ome.util.SqlAction;

import org.hibernate.Session;

/**
 * Single action performed by {@link DeleteState}.
 *
 * @author Josh Moore, josh at glencoesoftware.com
 * @since Beta4.2.3
 * @see IDelete
 */
public class DeleteStepFactory extends AbstractStepFactory {

    private final OmeroContext ctx;

    private final ExtendedMetadata em;

    public DeleteStepFactory(OmeroContext ctx, ExtendedMetadata em) {
        this.ctx = ctx;
        this.em = em;
    }

    public GraphStep create(int idx, List<GraphStep> stack, GraphSpec spec,
            GraphEntry entry, long[] ids) throws GraphException {
        return new DeleteStep(em, ctx, idx, stack, spec, entry, ids);
    }

    @Override
    public void onPostProcess(List<GraphStep> steps, SqlAction sql, Session session) {
        for (int i = 0; i < originalSize; i++) {
            GraphStep step = steps.get(i);
            if ("Image".equals(step.table)) {
                long[] ids = step.getIds();
                if (ids == null || ids.length == 0) {
                    continue;
                }
                QueryBuilder qb = new QueryBuilder();
                qb.select("i.fileset.id").from("Image", "i");
                qb.where().and("i.id = :id");
                qb.param("id", ids[ids.length-1]);
                Long rv = (Long) qb.query(session).uniqueResult();

                Long filesetId = null;
                if (rv != null) {
                    filesetId = rv;
                }
                
                steps.add(new DeleteValidation(ctx, em, step.idx, step.stack,
                        step.spec, step.entry, step.getIds(), "Fileset", filesetId));                
            }
        }
    }
}
