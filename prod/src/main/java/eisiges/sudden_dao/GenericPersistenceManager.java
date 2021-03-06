package eisiges.sudden_dao;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

/**
 * Intended to be subclassed for use as a persistence manager. It contains all boilerplate code for a class used to manage persistent Entities.
 * 
 * Usage:
 *	Extend this persistence manager class and annotate your bean as {@link Local} and {@link Stateless}
 *	Create a default constructor with a single call to super(Class.class); where Class is the name of the {@link Entity} you wish to manage
 * @author Sam McCollum
 * 
 * @param <T> The type of Object being managed
 * @param <K> The type used for the Entity's {@link Id}
 */
public class GenericPersistenceManager<T, K> {
	protected Class<T> cArg;
	protected String tableName;
	
	@PersistenceContext
	protected EntityManager em;
	
	public GenericPersistenceManager(Class<T> cArg, String tableName){
		this.cArg = cArg;
		this.tableName = tableName;
	}
	
	public GenericPersistenceManager(Class<T> cArg){
		this(cArg, cArg.getSimpleName());
	}
	
	/**
	 * Check whether or not an object with the given key (id) is in the database
	 * @param key the primary key whose existence to check for
	 * @return true if it exists in the database, falst if not
	 */
	public boolean containsKey(K key){
		if(key == null)
			return false;
		return (get(key) != null);
	}
	
	/**
	 * Persist and return the object
	 * @param data the entity instance being persisted
	 * @return the managed entity instance
	 */
	public T persist(T data){
		em.persist(data);
		em.flush();
		return data;
	}
	
	@SuppressWarnings("unchecked") // yes it's an unchecked cast, but we know what we're doing
	public K getId(T data) {
		return (K)em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(data);
	}

	/**
	 * Update an object in the database
	 * @param data The entity to be updated/saved
	 * @return the managed entity
	 */
	public T save(T data){
		return em.merge(data);
	}

	/**
	 * Create a {@link FindBuilder} instance for performing a find with a given entity type
	 * @param <X> the type of the entity being found
	 * @param type the class of the entity to be found
	 * @return a builder for a find query
	 */
	public <X> FindBuilder<X> find(Class<X> type) {
		return new FindBuilder<>(type, em);
	}

	/**
	 * Retrieve an object from the database with the given key (id)
	 * @param key primary key of the object to be retrieved
	 * @return the entity
	 */
	public T get(K key){
		return em.find(cArg, key);
	}
	/**
	 * Remove an object from the database
	 * @param data object to be removed
	 */
	public void remove(T data){
		em.remove(em.merge(data));
	}
	/**
	 * Save a collection of objects to the database
	 * @param data {@link Collection} of objects to be saved
	 */
	public void saveAll(Collection<T> data){
		for(T d : data)
			save(d);
	}
	/**
	 * Remove a collection of objects from the database
	 * @param data {@link Collection} of objects to be removed
	 */
	public void removeAll(Collection<T> data){
		for(T d : data)
			remove(d);
	}
	/**
	 * Retrieve a list of {@link T}, all of the ones in the database
	 * @return {@link List} of all {@link T} in the database
	 */
	public List<T> getAll(){
		TypedQuery<T> q = getAllQuery();
		return q.getResultList();
	}
	
	/**
	 * Checks whether or not the table is empty
	 * @return true if empty, false if not
	 */
	public boolean isTableEmpty(){
		List<T> results = getAllQuery().setMaxResults(2).getResultList();
		return (results.size() < 1);
	}

	/**
	 * Retrieve a query for all the objects in the database
	 * @return TypedQuery for selecting all
	 */
	public TypedQuery<T> getAllQuery(){
		return em.createQuery(getSelectAllQueryString(), cArg);
	}
	/**
	 * helper method to get the select all query string
	 * @return Query string for select all
	 */
	protected String getSelectAllQueryString(){
		return "SELECT data FROM "+tableName+" data";
	}

	/**
	 * Query a database for a {@link List} of all objects whose values match the keyObject's non-null object member variables
	 * @param keyObject the object to match
	 * @return {@link List} of objects which match the keyObject
	 */
	public List<T> getMatching(T keyObject) {
		return getMatchingQuery(keyObject).getResultList();
	}
	
	/**
	 * Query a database for all objects whose values match the keyObject's non-null object member variables
	 * @param keyObject the object to match
	 * @return {@link TypedQuery} of objects which match the keyObject
	 */
	public TypedQuery<T> getMatchingQuery(T keyObject){
		Field[] members = cArg.getDeclaredFields();
		Map<String, Object> importantFields = new TreeMap<>();
		for(Field member : members){
			try {
				member.setAccessible(true);
				if(!member.getType().isPrimitive()){
					Object val = member.get(keyObject);
					if(val != null && !Modifier.isFinal(member.getModifiers()) && !Modifier.isStatic(member.getModifiers()) && !(val instanceof Collection))
						importantFields.put(member.getName(), val);
				}
			} catch (Exception e){
				e.printStackTrace();
			}
		}
		StringBuilder queryBuilder = new StringBuilder(getSelectAllQueryString());
		int run = 0;
		for(Map.Entry<String, Object> field : importantFields.entrySet()){
			if(run == 0){
				queryBuilder.append(String.format(" where data.%s = :%s", field.getKey(), field.getKey()));
			}else{
				queryBuilder.append(String.format(" and data.%s = :%s", field.getKey(), field.getKey()));
			}
			++run;
		}
		//System.out.printf("Composed query string:\n%s\n", queryString);
		TypedQuery<T> q = em.createQuery(queryBuilder.toString(), cArg);
		for(Map.Entry<String, Object> field : importantFields.entrySet()){
			q.setParameter(field.getKey(), field.getValue());
		}
		return q;
	}
	
	private List<Field> getNonNullFields(T keyObject){
		Field[] members = cArg.getDeclaredFields();
		List<Field> importantFields = new LinkedList<>();
		for(Field member : members){
			try {
				member.setAccessible(true);
				if(!member.getType().isPrimitive()){
					Object val = member.get(keyObject);
					if(val != null)
						importantFields.add(member);
				}
			} catch (Exception e){
				e.printStackTrace();
			}
		}
		return importantFields;
	}
	
	public List<Object> getPropertyList(T keyObject){
		List<Object> list = new LinkedList<>();
		List<Field> importantFields = getNonNullFields(keyObject);
		if(importantFields.size() > 0){
			Field fiq = importantFields.get(0);
			fiq.setAccessible(true);
			for(T data : getAll()){
				try {
					list.add(fiq.get(data));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
		return list;
	}
}
