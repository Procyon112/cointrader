package org.cryptocoinpartners.schema.dao;

import java.util.List;
import java.util.UUID;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.NoResultException;
import javax.persistence.OptimisticLockException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.module.ApplicationInitializer;
import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.util.ConfigUtil;
import org.cryptocoinpartners.util.Visitor;
import org.hibernate.PersistentObjectException;
import org.hibernate.PropertyAccessException;
import org.hibernate.StaleObjectStateException;
import org.hibernate.TransientObjectException;
import org.hibernate.TransientPropertyValueException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;

public abstract class DaoJpa implements Dao, java.io.Serializable {
  /**
     * 
     */
  private static final long serialVersionUID = -3999121207747846784L;
  protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.persist");
  private static final int defaultBatchSize = 20;
  private static int retry;
  static {
    retry = ConfigUtil.combined().getInt("db.persist.retry");
  }
  @Inject
  protected transient Provider<EntityManager> entityManager;

  @Inject
  protected transient ApplicationInitializer application;

  @Inject
  private transient UnitOfWork unitOfWork;

  //protected EntityManager entityManager;

  public DaoJpa() {
    //  ParameterizedType genericSuperclass = (ParameterizedType) getClass().getGenericSuperclass();
    //this.entityClass = (Class) genericSuperclass.getActualTypeArguments()[1];
  }

  @Override
  public void queryEach(Visitor<Object[]> handler, String queryStr, Object... params) {
    try {
      queryEach(handler, defaultBatchSize, queryStr, params);
    } catch (Exception | Error ex) {

      log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryEach, full stack trace follows:", ex);
      throw ex;
    }
  }

  @Override
  @Transactional
  public void queryEach(Visitor<Object[]> handler, int batchSize, String queryStr, Object... params) {
    try {
      Query query = entityManager.get().createQuery(queryStr);
      if (params != null) {
        for (int i = 0; i < params.length; i++) {
          Object param = params[i];
          query.setParameter(i + 1, param); // JPA uses 1-based indexes
        }
      }
      query.setMaxResults(batchSize);
      for (int start = 0;; start += batchSize) {
        query.setFirstResult(start);
        List list = query.getResultList();
        if (list.isEmpty())
          return;
        for (Object row : list) {
          if (row.getClass().isArray() && !handler.handleItem((Object[]) row) || !row.getClass().isArray() && !handler.handleItem(new Object[]{row}))
            return;
        }
      }
    } catch (Exception | Error ex) {
      log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryEach, full stack trace follows:", ex);

      throw ex;

      // log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryEach, full stack trace follows:", ex);
      // ex.printStackTrace();

    }
  }

  @Override
  public <T> void queryEach(Class<T> resultType, Visitor<T> handler, String queryStr, Object... params) {
    queryEach(resultType, handler, defaultBatchSize, queryStr, params);
  }

  @Override
  @Transactional
  public <T> void queryEach(Class<T> resultType, Visitor<T> handler, int batchSize, String queryStr, Object... params) {
    try {
      TypedQuery<T> query = entityManager.get().createQuery(queryStr, resultType);
      if (params != null) {
        for (int i = 0; i < params.length; i++) {
          Object param = params[i];
          query.setParameter(i + 1, param); // JPA uses 1-based indexes
        }
      }
      query.setMaxResults(batchSize);
      for (int start = 0;; start += batchSize) {
        query.setFirstResult(start);
        List<T> list = query.getResultList();
        if (list.isEmpty())
          return;
        for (T row : list) {
          if (!handler.handleItem(row))
            return;
        }
      }
    } catch (Exception | Error ex) {

      log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryEach, full stack trace follows:", ex);
      throw ex;

    }
  }

  @Override
  @Transactional
  public <T> T namedQueryOne(Class<T> resultType, String namedQuery, Object... params) throws NoResultException {
    try {
      TypedQuery<T> query = entityManager.get().createNamedQuery(namedQuery, resultType);
      if (params != null) {
        for (int i = 0; i < params.length; i++) {
          Object param = params[i];
          query.setParameter(i + 1, param); // JPA uses 1-based indexes
        }
      }
      return query.getSingleResult();
    } catch (Exception | Error ex) {

      log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":namedQueryOne, full stack trace follows:", ex);
      throw ex;

    }

  }

  @Override
  @Transactional
  public <T> T queryZeroOne(Class<T> resultType, String queryStr, Object... params) {
    try {
      TypedQuery<T> query = entityManager.get().createQuery(queryStr, resultType);
      if (params != null) {
        for (int i = 0; i < params.length; i++) {
          Object param = params[i];
          query.setParameter(i + 1, param); // JPA uses 1-based indexes
        }
      }

      return query.getSingleResult();
    } catch (NoResultException x) {
      return null;
    } catch (Error | Exception ex) {
      log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryZeroOne, full stack trace follows:", ex);

      throw ex;
    }

  }

  @Transactional
  public <T> T queryOne(Class<T> resultType, String queryStr, Object... params) {
    try {

      TypedQuery<T> query = entityManager.get().createQuery(queryStr, resultType);
      if (params != null) {
        for (int i = 0; i < params.length; i++) {
          Object param = params[i];
          query.setParameter(i + 1, param); // JPA uses 1-based indexes
        }
      }
      return query.getSingleResult();
    } catch (NoResultException x) {
      return null;
    } catch (Error | Exception ex) {
      log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryOne, full stack trace follows:", ex);

      throw ex;
    }

  }

  @Override
  @Transactional
  public <T> List<T> queryList(Class<T> resultType, String queryStr, Object... params) {
    EntityManager em = null;
    try {

      TypedQuery<T> query = entityManager.get().createQuery(queryStr, resultType);
      if (params != null) {
        for (int i = 0; i < params.length; i++) {
          Object param = params[i];
          query.setParameter(i + 1, param); // JPA uses 1-based indexes
        }
      }
      return query.getResultList();
    } catch (TransientObjectException toe) {
      return null;
    } catch (Error | Exception ex) {
      log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryList, full stack trace follows:", ex);
      throw ex;
    }
  }

  @Override
  //  @Transactional
  // @com.google.inject.persist.Transactional
  public void detach(EntityBase... entities) {
    for (EntityBase entity : entities)
      try {

        // EntityBase existingEntity = entityManager.get().find(entity.getClass(), entity.getId());
        //if (existingEntity != null) {
        //entityManager.get().merge(entity);
        //entityManager.get().flush();
        ///    } else
        // update(entity);

        evict(entity);
        // entityManager.get().detach(entity);
        //    entityManager.get().detach(entity);

        //entityManager.get().flush();

        // log.debug("persisting entity " + entity.getClass().getSimpleName() + " " + entity);
        //   entityManager.get().getTransaction().commit();
      }

      catch (Exception | Error ex) {

        log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":detach, full stack trace follows:", ex);
        //
        throw ex;
        //ex.printStackTrace();

      }
  }

  @Override
  public EntityBase refresh(EntityBase... entities) {
    EntityBase localEntity = null;
    for (EntityBase entity : entities)
      try {

        // EntityBase existingEntity = entityManager.get().find(entity.getClass(), entity.getId());
        //if (existingEntity != null) {
        //entityManager.get().merge(entity);
        //entityManager.get().flush();
        ///    } else
        // update(entity);

        localEntity = restore(entity);
        // entityManager.get().detach(entity);
        //    entityManager.get().detach(entity);

        //entityManager.get().flush();

        // log.debug("persisting entity " + entity.getClass().getSimpleName() + " " + entity);
        //   entityManager.get().getTransaction().commit();
      }

      catch (Exception | Error ex) {

        log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":detach, full stack trace follows:", ex);
        //
        throw ex;
        //ex.printStackTrace();

      }

    return localEntity;
  }

  @Transactional
  @Override
  public <T> T find(Class<T> resultType, UUID id) {
    try {
      return entityManager.get().find(resultType, id);
    } catch (Error | Exception ex) {
      log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":find, full stack trace follows:", ex);

      throw ex;
    }

  }

  @Override
  @Transactional
  public boolean contains(EntityBase entity) {
    try {
      return entityManager.get().contains(entity);
    } catch (Error | Exception ex) {
      log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":contains, full stack trace follows:", ex);

      throw ex;
    }

  }

  @Transactional
  public <T> T getReference(Class<T> resultType, UUID id) {
    try {
      return entityManager.get().getReference(resultType, id);
    } catch (Error | Exception ex) {
      log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":getReference, full stack trace follows:", ex);

      throw ex;
    }

  }

  // @Override
  // @Transactional
  // @com.google.inject.persist.Transactional
  //  @Transactional
  //  @Inject
  @Override
  public void persistEntities(EntityBase... entities) throws Throwable {
    int attempt = 0;
    boolean persisted = false;
    for (EntityBase entity : entities) {
      try {
        //   long revision = entity.findRevisionById();
        // if (entity.getRevision() > revision) {
        insert(entity);
        persisted = true;
        //} else {
        //  log.trace("DapJpa - persistEntities: " + entity.getClass().getSimpleName() + " not peristed as entity revision " + entity.getRevision()
        //        + " is not greater than peristed revision " + revision + ". Entity " + entity.getId());
        // }

      } catch (OptimisticLockException | StaleObjectStateException ole) {
        attempt = entity.getAttempt() + 1;
        entity.setAttempt(attempt);

        if (attempt >= retry) {
          log.error(" " + this.getClass().getSimpleName() + ":persist attempt:" + entity.getAttempt() + " for " + entity + " " + retry
              + ", full stack trace follows:", ole);

          entity.setAttempt(0);

          throw ole;
        } else {
          log.trace(this.getClass().getSimpleName() + ":persist Later version of " + entity.getClass().getSimpleName() + " id: " + entity.getId()
              + " already persisted. Persist attempt " + entity.getAttempt() + " of " + retry);

          entity.setAttempt(0);
          entity.setPeristanceAction(PersistanceAction.NEW);
          application.getInsertQueue().add(entity);
          //      persist(false, entity);
        }

      } catch (LockTimeoutException lte) {

        attempt = entity.getAttempt() + 1;
        entity.setAttempt(attempt);

        if (attempt >= retry) {
          log.error(" " + this.getClass().getSimpleName() + ":persist attempt:" + entity.getAttempt() + " for " + entity + " " + retry
              + ", full stack trace follows:", lte);

          //    log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", lte);
          entity.setAttempt(0);
          throw lte;
        } else {
          log.info("Record locked version of " + entity.getClass().getSimpleName() + " id: " + entity.getId()
              + " already persisted. Persist attempt " + entity.getAttempt() + " of " + retry);
          //               entity.setRevision(0);
          entity.setPeristanceAction(PersistanceAction.NEW);
          application.getInsertQueue().add(entity);
          //  persist(false, entity);

        }

      } catch (Throwable ex) {
        //   log.debug("casuse:" + ex.getCause() + " ex " + ex);
        if (ex.getCause() != null && (ex.getCause() instanceof TransientPropertyValueException || ex.getCause() instanceof IllegalStateException)) {
          //.setVersion(entity.getVersion() + 1);
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          //  entity.setStartTime(entity.getDelay() * 2);

          if (attempt >= retry) {
            //log.error(" " + this.getClass().getSimpleName() + ":persist, Parent object not persisted for, attempting last ditch merge.");
            log.error(" " + this.getClass().getSimpleName() + ":persist attempt:" + entity.getAttempt() + " for " + entity + " " + retry
                + ", full stack trace follows:", ex);
            entity.setAttempt(0);
            // merge(entities);

            throw ex;
          } else {
            log.debug("Parent object not persisted for  " + entity.getClass().getSimpleName() + " id: " + entity.getId() + ". Persist attempt "
                + entity.getAttempt() + " of " + retry);
            //                   entity.setRevision(0);
            entity.prePersist();
            entity.setPeristanceAction(PersistanceAction.NEW);
            application.getInsertQueue().add(entity);
            //    persist(false, entity);
            //  persist(false, entity);
            // attempt++;
            // continue;
          }
        } else if (ex instanceof EntityExistsException || (ex.getCause() != null && ex.getCause().getCause() instanceof ConstraintViolationException)) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          //    entity.setStartTime(entity.getDelay() * 2);
          //   EntityBase dbEntity = entity.refresh();

          //dbEntity = entity;

          if (attempt >= retry) {
            log.error(" " + this.getClass().getSimpleName() + ":persist attempt:" + entity.getAttempt() + " for " + entity + " " + retry
                + ", full stack trace follows:", ex);
            //   log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ex);
            entity.setAttempt(0);
            throw ex;
          } else {
            log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                + " :persist, primary key for version " + entity.getVersion() + "  already present in db. Persist attempt " + entity.getAttempt()
                + " of " + retry);

            // entity.setAttempt(0);
            //                      entity.setRevision(0);
            entity.setPeristanceAction(PersistanceAction.MERGE);
            application.getMergeQueue().add(entity);
            // merge(false, entity);
          }

        } else if (ex.getCause() != null && ex.getCause() instanceof PersistentObjectException) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          // entity.setStartTime(entity.getDelay() * 2);
          // entity.setStartTime(entity.getDelay() * 2);

          //   update(entity);

          if (attempt >= retry) {
            log.error(" " + this.getClass().getSimpleName() + ":persist attempt:" + entity.getAttempt() + " for " + entity + " " + retry
                + ", full stack trace follows:", ex);
            entity.setAttempt(0);
            throw ex;
          } else {
            log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                + " :persist, presistent object expection, will attmpt to merge. Persist attempt " + entity.getAttempt() + " of " + retry);

            //              for (EntityBase entity : entities)
            // entity.setAttempt(0);
            //                        entity.setAttempt(0);
            //                     entity.setRevision(0);
            entity.setPeristanceAction(PersistanceAction.MERGE);
            application.getMergeQueue().add(entity);
            //   merge(false, entity);
          }

        } else if (ex.getCause() != null && ex.getCause() instanceof OptimisticLockException) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          //    entity.setStartTime(entity.getDelay() * 2);
          EntityBase dbEntity = entity.refresh();

          //dbEntity = entity;

          if (attempt >= retry) {
            log.error(" " + this.getClass().getSimpleName() + ":persist attempt:" + entity.getAttempt() + " for " + entity + " " + retry
                + ", full stack trace follows:", ex);
            entity.setAttempt(0);
            throw ex;
          } else {
            //entity.setAttempt(0);
            log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                + " :persist, primary key for version " + entity.getVersion() + "  already present in db with version " + dbEntity.getVersion()
                + ". Persist attempt " + entity.getAttempt() + " of " + retry);
            entity.setPeristanceAction(PersistanceAction.MERGE);
            application.getMergeQueue().add(entity);
            // merge(false, entity);

          }

          //for (EntityBase entity : entities)
          //    merge(entities);
        }

        else {
          log.error(" " + this.getClass().getSimpleName() + ":persist attempt:" + entity.getAttempt() + " for " + entity + " " + retry
              + ", full stack trace follows:", ex);
          //  log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ex);

          entity.setAttempt(0);
          throw ex;
        }
        //break;

      } finally {

        if (persisted)

          if (persisted) {
            entity.setAttempt(0);

            log.trace(" " + this.getClass().getSimpleName() + ":persist. Succefully persisted " + entity.getClass().getSimpleName() + " "
                + entity.getId());
          }

      }
      // break;
      // }
    }
  }

  //   unitOfWork.end();

  //     break;
  //   }

  //  @Override
  @Override
  public void persist(EntityBase... entities) {
    for (EntityBase entity : entities)
      log.trace("DaoJpa - Persist : Persit of " + entity.getClass().getSimpleName() + " " + entity.getId() + " called from class "
          + Thread.currentThread().getStackTrace()[2]);
    //let's clone the object as it could update and cause issues 
    //  SerializationUtils.clone(Object);

    persist(true, entities);

  }

  private void persist(boolean increment, EntityBase... entities) {

    try {
      for (EntityBase entity : entities) {
        //    entity.getDao().persistEntities(entity);
        // synchronized (entity) {
        if (entity != null) {
          entity.setPeristanceAction(PersistanceAction.NEW);
          //      if (increment)
          // entity.getDao().persistEntities(entity);
          //  SerializationUtils.clone(entity);
          EntityBase entityClone = entity.clone();
          entityClone.setDao(entity.getDao());
          application.getInsertQueue().add(entityClone);
          //  } else {
          //    application.getInsertQueue().addFirst(entity);
          //}
          log.trace("persisting " + entity.getClass().getSimpleName() + " id:" + entity.getId());
        }
        // }
      }

    } catch (Throwable e) {
      log.error("Unable to resubmit insert request in " + this.getClass().getSimpleName() + "insert, full stack trace follows:", e);
      //  e.printStackTrace();

    } finally {

    }

  }

  @Override
  public void delete(EntityBase... entities) {
    for (EntityBase entity : entities)
      log.trace("DaoJpa - Delete : delete of " + entity.getClass().getSimpleName() + " " + entity.getId() + " called from class "
          + Thread.currentThread().getStackTrace()[2]);

    delete(true, entities);

  }

  private void delete(boolean increment, EntityBase... entities) {

    try {
      for (EntityBase entity : entities) {
        //   synchronized (entity) {
        //    entity.getDao().persistEntities(entity);
        if (entity != null) {
          entity.setPeristanceAction(PersistanceAction.DELETE);
          //  if (increment)
          // entity.setRevision(entity.getRevision() + 1);
          entity.setStartTime(entity.getDelay());
          //   entity.getDao().deleteEntities(entity);
          EntityBase entityClone = entity.clone();

          // EntityBase entityClone = SerializationUtils.clone(entity);
          entityClone.setDao(entity.getDao());

          application.getMergeQueue().add(entityClone);

          log.debug("deleting " + entity.getClass().getSimpleName() + " id:" + entity.getId());
        }
        //  }
      }

    } catch (Error | Exception e) {
      log.error("Unable to resubmit delete request in " + this.getClass().getSimpleName() + "delete, full stack trace follows:", e);
      //  e.printStackTrace();

    } finally {

    }

  }

  /*
   * public class persistRunnable implements Runnable {
   * @Override public void run() { while (true) { try { EntityBase[] entities = insertQueue.take(); persistEntities(entities); } catch
   * (InterruptedException e) { Thread.currentThread().interrupt(); return; // supposing there is no cleanup or other stuff to be done } } } public
   * persistRunnable() { } }
   */

  /*
   * public class mergeRunnable implements Runnable {
   * @Override public void run() { while (true) { try { EntityBase[] entities = mergeQueue.take(); mergeEntities(entities); } catch
   * (InterruptedException e) { Thread.currentThread().interrupt(); return; // supposing there is no cleanup or other stuff to be done } } } public
   * mergeRunnable() { } }
   */
  //  @Nullable
  // @ManyToOne(optional = true)
  //, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  //cascade = { CascadeType.ALL })
  // @JoinColumn(name = "position")
  //    @Override
  //    public BlockingQueue<EntityBase[]> getInsertQueue() {
  //
  //        return insertQueue;
  //    }
  //
  //    @Override
  //    public BlockingQueue<EntityBase[]> getMergeQueue() {
  //
  //        return mergeQueue;
  //    }

  @Override
  public void merge(EntityBase... entities) {
    for (EntityBase entity : entities)
      log.trace("DaoJpa - Merge : Merge of " + entity.getClass().getSimpleName() + " " + entity.getId() + " called from class "
          + Thread.currentThread().getStackTrace()[2]);

    merge(false, entities);
  }

  // @Override
  private void merge(boolean increment, EntityBase... entities) {

    try {
      for (EntityBase entity : entities) {
        // synchronized (entity) {
        if (entity != null) {
          entity.setPeristanceAction(PersistanceAction.MERGE);
          // if (increment)
          //  entity.setRevision(entity.getRevision() + 1);
          //    entity.getDao().mergeEntities(entity);
          // we should only add to quuee to front of queue
          //   EntityBase entityClone = SerializationUtils.clone(entity);
          // entityClone.setDao(entity.getDao());
          EntityBase entityClone = entity.clone();
          entityClone.setDao(entity.getDao());
          application.getMergeQueue().add(entityClone);
          //  .add(c);
          // entity.getDao().mergeEntities(entity);
          //  application.getMergeQueue().put(entity);

          // }

          // else {
          //   application.getInsertQueue().addFirst(entity);
          //}

          log.trace("merging " + entity.getClass().getSimpleName() + " id:" + entity.getId());
        }
        // }
      }
    } catch (Throwable e) {

      log.error("Unable to resubmit merge request in " + this.getClass().getSimpleName() + " merge, full stack trace follows:", e);
      // e.printStackTrace();

    } finally {

    }

  }

  @Transactional
  public void insert(EntityBase entity) throws Throwable {

    // try {
    //entityManager.get().lock(entity, LockModeType.PESSIMISTIC_WRITE);
    //  synchronized (entity) {
    entityManager.get().persist(entity);
    // }
    //entityManager.get().flush();
    //} catch (Error | Exception ex) {
    //   log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":getReference, full stack trace follows:", ex);

    //  throw ex;
    // }

    // entityManager.get().getTransaction().commit();
    // entityManager.get().flush();
    // TODO Auto-generated method stub

  }

  @Transactional
  public EntityBase restore(EntityBase entity) {
    //EntityBase localEntity = entity;
    // localEntity = entity;
    ///  Object dBEntity;
    // if (!entityManager.get().contains(entity)) {
    //  try {
    EntityBase localEntity = entityManager.get().find(entity.getClass(), entity.getId());
    if (localEntity != null) {
      // entityManager.get().refresh(localEntity);
      //   entityManager.get().persist(localEntity);

      // localEntity
      // EntityBase newEntity = entityManager.get().merge(localEntity);
      // newEntity.setVersion(entity.getVersion());
      // EntityBase newEntityDecrement = entityManager.get().merge(localEntity);
      entity.setVersion(localEntity.getVersion());
      entityManager.get().merge(entity);
    }

    else {
      entityManager.get().persist(entity);

    }

    //   find(entity.getClass(), entity.getId());
    //  entityManager.get().merge(entity);

    // }

    // entityManager.get().refresh(entity);

    // localEntity = entityManager.get().merge(entity);

    //return null;

    // entityManager.get().refresh(localEntity);
    //   EntityBase returnEntity = 
    return entityManager.get().find(entity.getClass(), entity.getId());

    // } catch (Error | Exception ex) {
    //      return null;
    // throw ex;
    //     }
    // return entity;

    // TODO Auto-generated method stub

  }

  @Transactional
  public void update(EntityBase entity) {
    // try {
    // entityManager.get().lock(entity, LockModeType.PESSIMISTIC_WRITE);

    //   entity.getUpdateLock();

    //synchronized (entity) {
    entityManager.get().merge(entity);

    //}

    // } catch (Error | Exception ex) {
    //   throw ex;
    // }

    // TODO Auto-generated method stub

  }

  @Transactional
  public void evict(EntityBase entity) {
    try {
      // entityManager.get().(entity);
      entityManager.get().detach(entity);
    } catch (Error | Exception ex) {
      throw ex;
    }

    // TODO Auto-generated method stub

  }

  // @Override
  // @Transactional
  // @Override
  // @Transactional
  // @Inject

  @Override
  public void deleteEntities(EntityBase... entities) {
    int attempt = 0;
    boolean deleted = false;

    try {
      for (EntityBase entity : entities) {
        long revision = entity.findRevisionById();
        // long version = entity.findVersionById();

        if (entity.getRevision() >= revision) {
          //    attempt = entity.getAttempt();
          //
          //  EntityBase dbEntity = restore(entity);
          //.getClass(), entity.getId());

          // long dbVersion = target.findVersionById();
          //   if (dbEntity != null)
          //     entity.setVersion(dbEntity.getVersion());
          // refresh(entity);
          remove(entity);
          deleted = true;
        } else {
          log.trace("DapJpa - deleteEntities: " + entity.getClass().getSimpleName() + " not peristed as entity revision " + entity.getRevision()
              + " is not greater than peristed revision " + revision + ". Entity " + entity.getId());
        }

      }
    } catch (EntityNotFoundException | LockTimeoutException enf) {
      for (EntityBase entity : entities) {
        attempt = entity.getAttempt() + 1;
        entity.setAttempt(attempt);

        if (attempt >= retry) {
          log.error(this.getClass().getSimpleName() + ":delete attempt:" + entity.getAttempt() + " for " + entity + " " + retry
              + ", full stack trace follows:", enf);
          entity.setAttempt(0);
          throw enf;
        } else {

          EntityBase dbEntity = null;
          dbEntity = restore(entity);

          if (dbEntity != null) {
            delete(false, dbEntity);
            deleted = true;
          }
          //      .setPeristanceAction(PersistanceAction.MERGE);
          // merge(false, entity);
          //    EntityBase dbEntity = restore(entity);

          // find(entity.getClass(), entity.getId());

          // long dbVersion = target.findVersionById();
          //  if (dbEntity != null)
          ///    entity.setVersion(dbEntity.getVersion());
          // entity.merge();
          log.debug("Entity  " + entity.getClass().getSimpleName() + " id: " + entity.getId() + " not found in database to delete. Delete attempt "
              + entity.getAttempt() + " of " + retry);
        }
      }

    } catch (IllegalArgumentException ie) {
      for (EntityBase entity : entities) {
        attempt = entity.getAttempt() + 1;
        entity.setAttempt(attempt);
        if (attempt >= retry) {
          log.error(
              " " + this.getClass().getSimpleName() + ":delete attempt:" + entity.getAttempt() + " of " + retry + ", full stack trace follows:", ie);

          entity.setAttempt(0);
          throw ie;
        }
      }
      for (EntityBase entity : entities) {
        // entity.setAttempt(0);
        //      entity.setVersion(entity.getVersion() + 1);

        // entity.setStartTime(entity.getDelay());
        merge(false, entity);
        delete(false, entity);
        deleted = true;
        log.info(this.getClass().getSimpleName() + ":delete - Detached instance of " + entity.getClass().getSimpleName() + " id: " + entity.getId()
            + " already in database. Delete attempt " + entity.getAttempt() + " of " + retry);
      }

      //for (EntityBase entity : entities)
      //  delete(false, entities);

    } catch (OptimisticLockException | StaleObjectStateException ole) {

      //     unitOfWork.end();
      for (EntityBase entity : entities) {
        attempt = entity.getAttempt() + 1;
        entity.setAttempt(attempt);

        if (attempt >= retry) {
          log.error(
              " " + this.getClass().getSimpleName() + ":delete attempt:" + entity.getAttempt() + " of " + retry + ", full stack trace follows:", ole);

          entity.setAttempt(0);
          throw ole;
        }

        EntityBase dbEntity = null;
        try {
          dbEntity = restore(entity);
          if (dbEntity != null) {
            dbEntity.setPeristanceAction(PersistanceAction.DELETE);
            try {
              remove(dbEntity);
            } catch (Exception | Error ex) {
              delete(false, dbEntity);
            }

          } else {
            //   entity.setVersion(dbEntity.getVersion());
            entity.setPeristanceAction(PersistanceAction.DELETE);
            delete(false, entity);
            // deleted = true;
          }

        } catch (Exception | Error ex) {

          //
          entity.setPeristanceAction(PersistanceAction.DELETE);
          delete(false, entity);
          // deleted = true;
        }

        log.trace(this.getClass().getSimpleName() + ":delete - Later version of " + entity.getClass().getSimpleName() + " id: " + entity
            + " already merged. Delete attempt " + entity.getAttempt() + " of " + retry);

      }

      //  delete(false, entities);

    } catch (Exception | Error ex) {

      /*
       * if (ex.getMessage().equals("Entity not managed")) { for (EntityBase entity : entities) try { //entityManager.get().persist(entity); //
       * restore(entity); update(entity); remove(entity); // entityManager.get().refresh(entity); //entityManager.get().remove(entity); } catch
       * (Exception | Error ex1) { throw ex1; } // entity.refresh(); delete(entities); }
       */
      if (ex.getCause() != null && ex.getCause().getCause() instanceof TransientPropertyValueException) {
        for (EntityBase entity : entities) {
          // entity.setVersion(entity.getVersion() + 1);
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);

          log.info("Parent object not persisted for  " + entity.getClass().getSimpleName() + " id: " + entity.getId() + ". Delete attempt "
              + entity.getAttempt() + " of " + retry);
        }
        if (attempt >= retry) {
          log.error(" " + this.getClass().getSimpleName() + ":delete, full stack trace follows:", ex);
          for (EntityBase entity : entities) {

            entity.setAttempt(0);
          }
          throw ex;
        } else {
          for (EntityBase entity : entities) {
            // entity.setAttempt(entity.getAttempt() + 1);
            entity.setStartTime(entity.getDelay());
          }

          delete(false, entities);
          // attempt++;
          //continue;
        }
      } else if (ex.getCause() != null && ex.getCause().getCause() instanceof ConstraintViolationException) {
        for (EntityBase entity : entities) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);

          log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
              + " :delete, duplicate primary key already in db. Delete attempt " + entity.getAttempt() + " of " + retry);

        }
        if (attempt >= retry) {
          // log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
          for (EntityBase entity : entities) {
            entity.setAttempt(0);

          }
          // throw ex;
        } else {
          for (EntityBase entity : entities) {
            // entity.setAttempt(entity.getAttempt() + 1);
            entity.setStartTime(entity.getDelay());
          }

          delete(false, entities);
        }

      } else if (ex.getCause() != null && ex.getCause() instanceof PropertyAccessException) {
        for (EntityBase entity : entities) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);

          log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
              + " :delete issue with accessing proerpties, retrying delete. Delete attempt " + entity.getAttempt() + " of " + retry);

          // entity.setStartTime(entity.getDelay() * 2);
        }
        if (attempt >= retry) {
          log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
          for (EntityBase entity : entities) {

            entity.setAttempt(0);
          }
          throw ex;
        } else {
          for (EntityBase entity : entities) {
            // entity.setAttempt(entity.getAttempt() + 1);
            entity.setStartTime(entity.getDelay());
          }
          delete(false, entities);
        }
      } else if (ex.getCause() != null && ex.getCause() instanceof OptimisticLockException) {
        for (EntityBase entity : entities) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          if (attempt >= retry) {
            log.error(" " + this.getClass().getSimpleName() + ":delete attempt:" + entity.getAttempt() + " of " + retry
                + ", full stack trace follows:", ex);

            entity.setAttempt(0);
            throw ex;
          }
        }
        for (EntityBase entity : entities) {
          entity.setAttempt(0);
          //      entity.setVersion(entity.getVersion() + 1);

          entity.setStartTime(entity.getDelay());

          log.trace(this.getClass().getSimpleName() + ":delete - Later version of " + entity.getClass().getSimpleName() + " id: " + entity.getId()
              + " already in database. Delete attempt " + entity.getAttempt() + " of " + retry);
        }

        //for (EntityBase entity : entities)
        //  delete(false, entities);
      } else if (ex.getCause() != null && ex.getCause() instanceof IllegalArgumentException) {
        for (EntityBase entity : entities) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          if (attempt >= retry) {
            log.error(" " + this.getClass().getSimpleName() + ":delete attempt:" + entity.getAttempt() + " of " + retry
                + ", full stack trace follows:", ex);

            entity.setAttempt(0);
            throw ex;
          }
        }
        for (EntityBase entity : entities) {
          // entity.setAttempt(0);
          //      entity.setVersion(entity.getVersion() + 1);

          // entity.setStartTime(entity.getDelay());
          merge(false, entity);
          delete(false, entity);
          log.info(this.getClass().getSimpleName() + ":delete - Detached instance of " + entity.getClass().getSimpleName() + " id: " + entity.getId()
              + " already in database. Delete attempt " + entity.getAttempt() + " of " + retry);
        }

        //for (EntityBase entity : entities)
        //  delete(false, entities);
      } else {
        log.error(" " + this.getClass().getSimpleName() + ":delete, full stack trace follows:", ex);
        for (EntityBase entity : entities) {

          entity.setAttempt(0);
        }
        throw ex;
      }

      //     unitOfWork.end();

    } finally {
      if (deleted)
        for (EntityBase entity : entities) {
          entity.setAttempt(0);
          log.trace(this.getClass().getSimpleName() + ":delete. Succefully deleted " + entity.getClass().getSimpleName() + " " + entity.getId());
        }

    }
    // break;
    // }
    //  break;

    // ex.printStackTrace();

  }

  @Override
  public void mergeEntities(EntityBase... entities) {
    int attempt = 0;
    boolean merged = false;
    try {
      for (EntityBase entity : entities) {
        long revision = entity.findRevisionById();
        if (entity.getRevision() > revision) {
          update(entity);
          merged = true;
        } else {
          log.trace("DapJpa - mergeEntities: " + entity.getClass().getSimpleName() + " not peristed as entity revision " + entity.getRevision()
              + " is not greater than peristed revision " + revision + ". Entity " + entity.getId());
        }

      }
    } catch (EntityNotFoundException | IllegalArgumentException | LockTimeoutException enf) {
      for (EntityBase entity : entities) {
        attempt = entity.getAttempt() + 1;
        entity.setAttempt(attempt);

        //  attempt = entity.getAttempt() + 1;
        //   entity.setAttempt(0);
        //  entity.setStartTime(entity.getDelay() * 2);f

        log.info(this.getClass().getSimpleName() + "Entity  " + entity.getClass().getSimpleName() + " id: " + entity.getId()
            + " not found in database, persisting. Merge attempt " + entity.getAttempt() + " of " + retry);
        if (attempt >= retry) {
          log.error(" " + this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " " + entity.getClass().getSimpleName()
              + " for " + entity + " " + retry + ", full stack trace follows:", enf);

          entity.setAttempt(0);
          throw enf;
        } else {
          //try {
          //    restore(entity);
          // } catch (Exception | Error ex1) {
          //     log.error(" " + this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " of " + retry
          //           + ", unable to restore entity " + entity.getClass().getSimpleName() + " id " + entity.getId());
          // }
          entity.prePersist();
          entity.setPeristanceAction(PersistanceAction.MERGE);
          application.getMergeQueue().add(entity);
          //   persist(false, entity);
          //  EntityBase dbEntity = null;
          /*
           * try { dbEntity = restore(entity); } catch (Exception | Error ex) { // entity.setPeristanceAction(PersistanceAction.MERGE); merge(false,
           * entity); return; } if (dbEntity != null) merge(false, entity); else persist(false, entity);
           */
          //  entity.setPeristanceAction(PersistanceAction.NEW);
          //persist(false, entity);
          //merge(false, entity);
          //      return;

          //  log.error("error", ex);
          //   }
          //find(entity.getClass(), entity.getId());

          // long dbVersion = target.findVersionById();
          //   if (dbEntity != null)
          //        entity.setVersion(dbEntity.getVersion());
          // entity.refresh();
          //if (dbEntity != null) {
          //  dbEntity.setPeristanceAction(PersistanceAction.MERGE);
          //  merge(false, dbEntity);
          // }

          //  entity.setAttempt(0);
          // well it may be in db put not in persistance context
          //entity.setRevision(0);
          // entity.setPeristanceAction(PersistanceAction.NEW);
          //// persist(false, entity);
        }
      }
    } catch (OptimisticLockException | StaleObjectStateException ole) {

      //     unitOfWork.end();
      for (EntityBase entity : entities) {
        attempt = entity.getAttempt() + 1;
        entity.setAttempt(attempt);
        if (attempt >= retry) {
          log.error(this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " for " + entity + " " + retry
              + ", full stack trace follows:", ole);
          entity.setAttempt(0);
          throw ole;
        } else {
          EntityBase dbEntity = null;
          try {
            dbEntity = restore(entity);
            if (dbEntity != null)
              entity.setVersion(dbEntity.getVersion());
            entity.setPeristanceAction(PersistanceAction.MERGE);
            application.getMergeQueue().add(entity);

            //   merge(false, entity);

          } catch (Exception | Error ex) {

            //
            entity.setPeristanceAction(PersistanceAction.MERGE);
            application.getMergeQueue().add(entity);
            //    merge(false, entity);
          }
          //     return;

          //  log.error("error", ex);
          //   }
          //     EntityBase dbEntity = restore(entity);
          //find(entity.getClass(), entity.getId());

          // long dbVersion = target.findVersionById();
          //  if (dbEntity != null)
          //  entity.setVersion(dbEntity.getVersion());
          //
          //}
          // for (EntityBase entity : entities) {
          //  EntityBase dbEntity = find(entity.getClass(), entity.getId());

          //   dbEntity.setVersion(0);
          //  if (dbEntity instanceof Fill) {
          //        Fill dbfill = (Fill) dbEntity;
          ///        Fill fill = (Fill) entity;
          //.setOpenVolumeCount(fill.getOpenVolumeCount());

          //       merge(false, dbEntity);
          //   }

          // update(dbEntity);
          // EntityBase dbEntity = entity.refresh();
          // if (dbEntity != null) {
          //      entity.setVersion(dbEntity.getVersion());
          //      merge(dbEntity);
          //  } //else
          // entity.setVersion(entity.getVersion() + 1);
          /*
           * if (attempt >= retry) { log.error(this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " of " + retry +
           * ", unable to merge " + entity.getClass().getSimpleName() + " with id " + entity.getId()); entity.setAttempt(0); // throw ole; } else {
           * //} //for (EntityBase entity : entities) { entity.setVersion(Math.max(entity.findVersionById() + 1, entity.getVersion() + 1));
           *///  entity.setStartTime(entity.getDelay() * 2);
             //   entity.setAttempt(0);
          log.trace(this.getClass().getSimpleName() + ":merge - Later version of " + entity.getClass().getSimpleName() + " id: " + entity
              + " already merged. Merge attempt " + entity.getAttempt() + " of " + retry);
          //  entity.setRevision(0);
          //   merge(false, entity);
          // mergeEntities(entities);
          //update(entity);
          //       entity.setPeristanceAction(PersistanceAction.MERGE);
          // merge(entity);
          // merge(false, entity);
        }
      }

    } catch (Exception | Error ex) {
      //    log.debug(" cause: " + ex.getCause() + "cause casuse" + ex.getCause().getCause() + "ex" + ex);
      if (ex.getCause() != null && ex.getCause() instanceof TransientPropertyValueException
          || (ex.getCause() != null && ex.getCause().getCause() != null && ex.getCause().getCause() instanceof TransientPropertyValueException)) {
        for (EntityBase entity : entities) {
          // entity.setVersion(entity.getVersion() + 1);
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);

          log.info("Parent object not persisted for  " + entity.getClass().getSimpleName() + " id: " + entity.getId() + ". Merge attempt "
              + entity.getAttempt() + " of " + retry);

          if (attempt >= retry) {
            log.error(" " + this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " for " + entity + " " + retry
                + ", full stack trace follows:", ex);

            //log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
            entity.setAttempt(0);
            throw ex;
          } else {

            // entity.setRevision(0);
            //                   entity.setRevision(0);
            entity.prePersist();
            entity.setPeristanceAction(PersistanceAction.MERGE);

            application.getMergeQueue().add(entity);
            //     merge(false, entities);

          }
        }
      } else if (ex.getCause() != null && ex.getCause().getCause() instanceof ConstraintViolationException) {
        for (EntityBase entity : entities) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);

          log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
              + " :merge, duplicate primary key already in db. Persist attempt " + entity.getAttempt() + " of " + retry);

          if (attempt >= retry) {
            log.error(" " + this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " for " + entity + " " + retry
                + ", full stack trace follows:", ex);

            //  log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
            entity.setAttempt(0);

            throw ex;
          } else {
            //      entity.setRevision(0);
            entity.setPeristanceAction(PersistanceAction.MERGE);

            application.getMergeQueue().add(entity);
            //    merge(false, entities);
          }
        }

      } else if (ex.getCause() != null && ex.getCause() instanceof PropertyAccessException) {
        for (EntityBase entity : entities) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);

          log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
              + " :merge, issue with accessing proerpties, retrying merge. Persist attempt " + entity.getAttempt() + " of " + retry);

          if (attempt >= retry) {
            log.error(" " + this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " for " + entity + " " + retry
                + ", full stack trace follows:", ex);

            // log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
            entity.setAttempt(0);
            throw ex;
          } else {
            //     entity.setRevision(0);
            entity.setPeristanceAction(PersistanceAction.MERGE);

            application.getMergeQueue().add(entity);
            //  merge(false, entities);
          }
        }
      } else if (ex.getCause() != null && ex.getCause() instanceof OptimisticLockException) {
        for (EntityBase entity : entities) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          if (attempt >= retry) {
            log.error(" " + this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " for " + entity + " " + retry
                + ", full stack trace follows:", ex);

            entity.setAttempt(0);
            throw ex;
          } else {
            EntityBase dbEntity = null;
            try {
              dbEntity = restore(entity);
            } catch (Exception | Error ex1) {
              entity.setPeristanceAction(PersistanceAction.MERGE);
              application.getMergeQueue().add(entity);
              //   merge(false, entity);
              return;

              //  log.error("error", ex);
            }
            // }
            // for (EntityBase entity : entities) {
            // 
            // EntityBase dbEntity = restore(entity);
            //find(entity.getClass(), entity.getId());

            // long dbVersion = target.findVersionById();
            //   if (dbEntity != null)
            //   entity.setVersion(entity.getVersion() + 1);

            //      EntityBase dbEntity = find(entity.getClass(), entity.getId());
            //     if (dbEntity != null)
            //   dbEntity.setVersion(0);
            // merge(false, dbEntity);
            entity.setVersion(dbEntity.getVersion());
            //  update(dbEntity);

            // entity.setStartTime(entity.getDelay() * 2);
            //  entity.setAttempt(0);
            log.trace(this.getClass().getSimpleName() + ":merge - Later version of " + entity.getClass().getSimpleName() + " id: " + entity
                + " caused by already merged. Merge attempt " + entity.getAttempt() + " of " + retry);
            //           entity.setRevision(0);
            //  entity.setPeristanceAction(PersistanceAction.MERGE);

            //    merge(false, entity);
            // mergeEntities(entities);
            //
            // update(entity);
            //persist(false, entities);
          }
        }
      } else {

        log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
        for (EntityBase entity : entities)
          entity.setAttempt(0);
        throw ex;
      }

      //     unitOfWork.end();

    } finally {
      if (merged)
        for (EntityBase entity : entities) {

          entity.setAttempt(0);
          log.trace(" " + this.getClass().getSimpleName() + ":merge. Succefully merged " + entity.getClass().getSimpleName() + " " + entity);
        }

    }

  }

  /**
   * returns a single result entity. if none found, a javax.persistence.NoResultException is thrown.
   */
  // @Override
  //    public <T> T queryOne(Class<T> resultType, String queryStr, Object... params) {
  //
  //        final TypedQuery<T> query = entityManager.get().createQuery(queryStr, resultType);
  //        if (params != null) {
  //            for (int i = 0; i < params.length; i++) {
  //                Object param = params[i];
  //                query.setParameter(i + 1, param); // JPA uses 1-based indexes
  //            }
  //        }
  //        return query.getSingleResult();
  //
  //    }

  @Transactional
  public void remove(EntityBase entity) {

    // if (!em.contains(entity)) {
    //   System.out.println("delete() entity not managed: " + entity);
    // utx.begin();

    //   EntityBase target = entity;
    //  em.remove(target);
    //    utx.commit();

    //  EntityBase localEntity;
    // synchronized (entity) {
    //    if (!entityManager.get().contains(entity))
    //      try {

    //        target = entityManager.get().merge(entity);

    //  } catch (OptimisticLockException | StaleObjectStateException ole) {
    //    EntityBase dbEntity = null;
    //   try {
    //     dbEntity = restore(entity);
    //  } catch (Exception | Error ex) {
    //     entity.setPeristanceAction(PersistanceAction.MERGE);

    //    merge(false, entity);
    //     return;

    //  log.error("error", ex);

    //  EntityBase dbEntity = restore(entity);

    // long dbVersion = target.findVersionById();
    //   if (dbEntity != null)
    //      entity.setVersion(dbEntity.getVersion());
    //  target = entityManager.get().merge(entity);
    //  }

    ///  entityManager.get().refresh(entity);

    //   if (localEntity != null)
    // entityManager.get().refresh(target);
    entityManager.get().remove(entityManager.get().contains(entity) ? entity : entityManager.get().merge(entity));
    // entityManager.get().remove(entity);
    // }
  }

  @Override
  public <T extends EntityBase> T findById(Class<T> resultType, UUID id) throws NoResultException {
    try {
      return queryOne(resultType, "select x from " + resultType.getSimpleName() + " x where x.id = ?1", id);
    } catch (Exception | Error ex) {
      log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":findById, full stack trace follows:", ex);
      throw ex;
      //break;

    }
  }

  @Override
  public <T> Long findRevisionById(Class<T> resultType, UUID id) throws NoResultException {
    try {
      Integer revision = queryOne(Integer.class, "select revision from " + resultType.getSimpleName() + " x where x.id = ?1", id);
      return revision.longValue();
    } catch (Exception | Error ex) {
      //log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":findById, full stack trace follows:", ex);
      throw ex;
      //break;

    }
  }

  @Override
  public <T> Long findVersionById(Class<T> resultType, UUID id) throws NoResultException {
    try {
      Long version = queryOne(Long.class, "select version from " + resultType.getSimpleName() + " x where x.id = ?1", id);
      return version.longValue();
    } catch (Exception | Error ex) {
      //log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":findById, full stack trace follows:", ex);
      throw ex;
      //break;

    }
  }

}
