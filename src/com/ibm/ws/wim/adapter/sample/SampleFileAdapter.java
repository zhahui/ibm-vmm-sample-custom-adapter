/************** Begin Copyright - Do not add comments here **************
 * IBM Confidential
 *
 * OCO Source Materials
 *
 * Virtual member manager
 *
 * (C) Copyright IBM Corp. 2005-2006
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 *
 ***************************** End Copyright ****************************/
/************************************************************************
 * %Z% %I% %W% %G% %U% [%H% %T%]
 * "File version %I%, last changed %E%"
 *
 * File Name: SampleFileAdapter.java
 *
 * Description: Sample file based adapter.
 *
 * Change History:
 *
 * mm/dd/yy userid   track 	change history description here
 * -------- ------   ----- -------------------------------------------
 ******************************************************************************/
package com.ibm.ws.wim.adapter.sample;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;

import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import com.ibm.websphere.wim.ConfigConstants;
import com.ibm.websphere.wim.exception.CertificateMapNotSupportedException;
import com.ibm.websphere.wim.exception.EntityAlreadyExistsException;
import com.ibm.websphere.wim.exception.EntityNotFoundException;
import com.ibm.websphere.wim.exception.InitializationException;
import com.ibm.websphere.wim.exception.InvalidArgumentException;
import com.ibm.websphere.wim.exception.InvalidEntityTypeException;
import com.ibm.websphere.wim.exception.PasswordCheckFailedException;
import com.ibm.websphere.wim.exception.RemoveEntityException;
import com.ibm.websphere.wim.exception.WIMApplicationException;
import com.ibm.websphere.wim.exception.WIMException;
import com.ibm.websphere.wim.ras.WIMLogger;
import com.ibm.websphere.wim.ras.WIMMessageHelper;
import com.ibm.websphere.wim.ras.WIMMessageKey;
import com.ibm.websphere.wim.ras.WIMTraceHelper;
import com.ibm.websphere.wim.util.SDOHelper;
import com.ibm.websphere.wim.util.UniqueIdGenerator;
import com.ibm.wsspi.wim.GenericHelper;
import com.ibm.wsspi.wim.SchemaHelper;



import commonj.sdo.DataGraph;
import commonj.sdo.DataObject;
import commonj.sdo.Property;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.sdo.util.SDOUtil;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.ExtendedMetaData;
import org.eclipse.emf.ecore.xmi.XMLResource;

/**
 * This class implements the repository specific abstract methods of 
 * AbstractAdapterImpl class. This class uses file system as the 
 * repository. The SDO dataobject is serialized to the configured file.
 * The data structure used to process the entities is SDO dataobject. 
 * <br>
 * 
 * For this sample file adapter, uniqueName is same as externalName and 
 * uniqueId is same as externalId. uniqueId is generated using
 * {@link com.ibm.websphere.wim.util.UniqueIdGenerator}. If a repository
 * supports some attribute which is unique and non-reusable then the value
 * of that attribute can be used as uniqueId.
 * <br>
 * 
 * This sample adapter does not support organization hierarchy (entity type
 * OrgContainer). The adapter is written to work in a single server 
 * environment. This could be used in a cluster environment, but the file 
 * repository and data will not be synchronized at runtime. Depending on 
 * the location of the file, the file might get synchronized when node-synch
 * takes place.
 * <br>
 * 
 * The data is saved to the file every time a create, update or delete
 * operations take place. This adapter compares the data in file repository 
 * in case-insensitive mode. The group members are stored within the group 
 * dataobject using the "members" dataobject. 
 * <p>
 * 
 * This adapter uses following custom property:
 * <code>
 *   fileName : full path of the file repository.
 * </code>
 * 
 * @author Ranjan Kumar
 */
public class SampleFileAdapter extends AbstractAdapterImpl
{
    //---------------------------------------------------------------
    // C O P Y R I G H T
    //---------------------------------------------------------------
    /**
     * The Copyright.
     */
    static final String COPYRIGHT_NOTICE = 
      com.ibm.websphere.wim.copyright.IBMCopyright.COPYRIGHT_NOTICE_SHORT_2005;

    //---------------------------------------------------------------
    // P R I V A T E    S T A T I C    C O N S T A N T S
    //---------------------------------------------------------------
    /**
     * The class name for this class (used for logging and tracing).
     */
    private static final String CLASSNAME = SampleFileAdapter.class.getName();

    /**
     * The trace logger for this class.
     */
    private static final Logger trcLogger = 
        WIMLogger.getTraceLogger(CLASSNAME);

    /**
     * Default file name of the file repository.
     */
    private static final String DEFAULT_FILE_NAME = "sampleFileRepository.xml";

    /**
     * Initial level for nested group operations.
     */
    private static final int INITIAL_CURRENT_LEVEL = 0;

    /**
     * Constant to indicate that the dataobject is an entity. 
     */
    private static final Integer ENTITY_TYPE = new Integer(0);
    
    /**
     * Constant to indicate that the dataobject is an identifier.
     */
    private static final Integer IDENTIFIER_TYPE = new Integer(1);

    
    //---------------------------------------------------------------
    // P R I V A T E    V A R I A B L E S
    //---------------------------------------------------------------

    /**
     * The file name of the file repository.
     */
    private String fileName = null;

    /**
     * A map to store entity uniqueName to entity dataobject mapping.
     */
    private Map entityDN2DO = Collections.synchronizedMap(new HashMap());

    /**
     * A map to store entity uniqueId to entity uniqueName mapping.
     */
    private Map entityID2DN = Collections.synchronizedMap(new HashMap());

    /**
     * A map to store the entity type to its reference properties (and its
     * dataobject type) mapping.
     */
    private Map entityReference = Collections.synchronizedMap(new HashMap());

    /**
     * The datagraph which contains the root dataobject and entity dataobjects
     * of the file data.
     */
    private DataGraph entityDG = null;

    /**
     * The root dataobject of the file data.
     */
    private DataObject entityRoot = null;

    /**
     * The total number of entities in the file repository.
     */
    private int numOfEntities = 0;

    
    //---------------------------------------------------------------
    // C O N S T R U C T O R
    //---------------------------------------------------------------
    /**
     * Default constructor.
     */
    public SampleFileAdapter()
    {
    }

    //---------------------------------------------------------------
    // S P I    M E T H O D S
    //---------------------------------------------------------------

    /**
     * Initialize the adapter with the specified repository configuration.
     * 
     * @param reposConfig The DataObject which contains configuration data of
     *            this adapter.
     * 
     * @see AbstractAdapterImpl#initialize(commonj.sdo.DataObject)
     */
    public void initialize(DataObject reposConfig) throws WIMException
    {
        // call the super class initializer
        super.initialize(reposConfig);

        final String METHODNAME = "<init>";

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, METHODNAME,
                "repositoryConfigProps=" + repositoryConfigProps);
        }

        // get the filename for the file repository
        fileName = (String) repositoryConfigProps.get(
            ConfigConstants.CONFIG_PROP_FILE_NAME.toLowerCase());

        // filename is not set, use the default filename
        if ((fileName == null) || (fileName.trim().length() == 0)) {
            fileName = DEFAULT_FILE_NAME;
        }

        // initialize the supported entity reference properties
        initReferenceProperties();
        
        // load the entities from file repository
        loadFileData();

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, METHODNAME, "fileName=" + fileName);
        }
    }

    //---------------------------------------------------------------
    // A B S T R A C T    M E T H O D S  I M P L E M E N T A T I O N
    //---------------------------------------------------------------

    /**
     * @see AbstractAdapterImpl#isValidCustomProperty(String, String)
     */
    public boolean isValidCustomProperty(String key, String value)
    {
        // null or empty key is invalid
        // only key allowed is "fileName"
        if ((key != null) && (key.trim().length() != 0) && 
            normalizedStringsAreEqual(
                ConfigConstants.CONFIG_PROP_FILE_NAME, key)) {
            
            // for fileName property, verify that the file name is valid
            if (isValidFileName(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @see AbstractAdapterImpl#entityAlreadyExists(DataObject)
     */
    public boolean entityAlreadyExists(DataObject entity)
    {
        DataObject identifier = entity.getDataObject(DO_IDENTIFIER);
        String uniqueName = identifier.getString(PROP_UNIQUE_NAME);
        String uniqueId = getEntityID(entity);

        return entityAlreadyExists(uniqueId, uniqueName);
    }

    /**
     * @see AbstractAdapterImpl#entityAlreadyExists(String, String)
     */
    public boolean entityAlreadyExists(String uniqueId, String uniqueName)
    {
        // return true if uniqueId or uniqueName is present in the 
        // repository(cache) 
        if (((uniqueId != null) && (entityID2DN.containsKey(uniqueId))) || 
            ((uniqueName != null) && 
                (containsNormalizedKey(entityDN2DO, uniqueName)))) {
            return true;
        }
        else {
            return (normalizedStringsAreEqual(baseEntryName, uniqueName)) ?
                true : false;
        }
    }

    /**
     * @see AbstractAdapterImpl#entityMustExist(String, String)
     */
    public boolean entityMustExist(String uniqueId, String uniqueName)
        throws EntityNotFoundException
    {
        if (!entityAlreadyExists(uniqueId, uniqueName)) {
            throw new EntityNotFoundException(WIMMessageKey.ENTITY_NOT_FOUND,
                WIMMessageHelper.generateMsgParms(uniqueName), Level.SEVERE,
                CLASSNAME, "entityMustExist");
        }
        return true;
    }

    /**
     * @see AbstractAdapterImpl#groupMustExist(String, String)
     */
    public boolean groupMustExist(String uniqueId, String uniqueName)
        throws WIMException
    {
        // first check if the entity exists
        if (entityMustExist(uniqueId, uniqueName)) {
            DataObject grpDO = (uniqueId != null) ?
                getEntityByUniqueId(uniqueId) :
                getEntityByUniqueName(uniqueName);

            // check that the entity is of entity type Group or its sub type
            if (!SchemaHelper.isGroupType(grpDO.getType().getName())) {
                String param = (uniqueId != null) ? uniqueId : uniqueName;
                throw new InvalidEntityTypeException( 
                    WIMMessageKey.ENTITY_IS_NOT_A_GROUP, 
                    WIMMessageHelper.generateMsgParms(param), 
                    Level.SEVERE, CLASSNAME, "groupMustExist");
            }
        }
        return true;
    }

    /**
     * @see AbstractAdapterImpl#memberMustExist(String, String)
     */
    public boolean memberMustExist(String uniqueId, String uniqueName)
        throws WIMException
    {
        return entityMustExist(uniqueId, uniqueName);
    }

    /**
     * @see AbstractAdapterImpl#validateReferenceProperties(DataObject)
     */
    public void validateReferenceProperties(DataObject entity)
        throws WIMException
    {
        // for all the reference properties of this entity type
        // if the reference property is set, then validate that the
        // entity with uniqueName in the reference property exists
        EClass eClass = SchemaHelper.getEClass(entity.getType());
        EList references = eClass.getEAllReferences();
        for (int j = 0; j < references.size(); j++) {
            EReference eRef = (EReference) references.get(j);
            String refName = eRef.getName();
            String refType = eRef.getEType().getName();

            // ignore the reference property if it is not set
            // or if we do not need to validate some of the reference properties
            if (!entity.isSet(refName)
                || (!DO_ENTITY.equals(refType) && !DO_GROUP.equals(refType) &&
                    !SchemaHelper.isReferenceProperty(refName))) {
                continue;
            }

            // reference property is singlevalued, validate the value
            if (eRef.getUpperBound() == 1) {
                entityMustExist(null, 
                    getUniqueName(entity.getDataObject(refName)));
            }
            else {
                // reference property is multivalued, validate all the values
                List refDOs = entity.getList(refName);
                for (int k = 0; k < refDOs.size(); k++) {
                    String refDN = getUniqueName((DataObject) refDOs.get(k));
                    entityMustExist(null, refDN);
                }
            }
        } // for each reference properties
    }

    /**
     * @see AbstractAdapterImpl#getUniqueName(DataObject)
     */
    public String getUniqueName(DataObject entity)
        throws EntityNotFoundException
    {
        String uniqueName = null;

        if (entity != null) {
            // if the entity is of type identifier
            if (DO_IDENTIFIER_TYPE.equals(entity.getType().getName())) {
                // externalId and uniqueId has precedence over uniqueName and  
                // externalName.
                // if externalId or uniqueId is set then get the entity with
                // this id then get the uniqueName, else get the uniqueName 
                // from entity dataobject
                String id = entity.getString(PROP_EXTERNAL_ID);
                if (id == null) {
                    id = entity.getString(PROP_UNIQUE_ID);
                }
                if (id != null) {
                    uniqueName = getUniqueNameForUniqueId(id);
                }
                else {
                    uniqueName = entity.getString(PROP_UNIQUE_NAME);
                    if (uniqueName == null) {
                        uniqueName = entity.getString(PROP_EXTERNAL_NAME);
                    }
                }
            }
            else {
                // dataobject is an entity dataobject, get its identifier
                uniqueName = 
                    getUniqueName(entity.getDataObject(DO_IDENTIFIER));
            }
        }

        return uniqueName;
    }

    /**
     * @see AbstractAdapterImpl#createEntity(String, DataObject)
     */
    public synchronized DataObject createEntity(String entityType,
        DataObject entity) throws WIMException
    {
        final String METHODNAME = "createEntity";

        boolean logEnabled = trcLogger.isLoggable(Level.FINER);
        if (logEnabled) {
            trcLogger.entering(CLASSNAME, METHODNAME, entityType);
        }

        // return dataobject
        DataObject returnRoot = null;

        // remove the "groups" and "members" entity dataobjects, if set
        if (entity.isSet(DO_GROUPS)) {
            // remove the "groups" dataobject from the entity
            entity.unset(DO_GROUPS);
        }

        if (SchemaHelper.isGroupType(entityType) && entity.isSet(DO_MEMBERS)) {
            // remove the "members" dataobject from the entity
            entity.unset(DO_MEMBERS);
        }

        /*
         * To get specific properties of the entity, call the get methods
         * on the entity dataobject.
         * String cn = entity.getString("cn");
         * // multivalued properties are stored in a List
         * List descriptions = entity.getList("description");
         */
        
        // set the externalName of the entity to be same as uniqueName
        DataObject identifier = entity.getDataObject(DO_IDENTIFIER);
        String uniqueName = identifier.getString(PROP_UNIQUE_NAME);
        identifier.setString(PROP_EXTERNAL_NAME, uniqueName);

        // set the create timestamp
        entity.setString(PROP_CREATE_TIMESTAMP, getDateString());

        // for LoginAccount entities, hash the password
        if (SchemaHelper.isLoginAccountType(entityType)) {
            if (entity.isSet(PROP_PASSWORD)) {
               entity.set(PROP_PASSWORD, hash(entity.getBytes(PROP_PASSWORD)));
            }
        }

        if (entityDG == null) {
            // this is the first entity in the file repository
            if (logEnabled) {
                trcLogger.logp(Level.FINER, CLASSNAME, METHODNAME,
                    "Creating first entity in the file: entityType="
                        + entityType);
            }

            // initialize the entity root and entity datagraph
            entityRoot = SDOHelper.createRootDataObject();
            entityDG = entityRoot.getDataGraph();
        }

        // add the entity to the current list of entities
        entityRoot.getList(DO_ENTITIES).add(entity);

        // generate the uniqueId if it is not set
        // other repositories might generate their own unique id, which
        // can be obtained after the entity is created in the repository
        String uniqueId = getEntityID(entity);
        if ((uniqueId == null) || (uniqueId.trim().length() == 0)) {
            uniqueId = UniqueIdGenerator.newUniqueId();

            // set the uniqueId and externalId to be the same
            identifier.setString(PROP_UNIQUE_ID, uniqueId);
            identifier.setString(PROP_EXTERNAL_ID, uniqueId);
        }

        // update the number of entities
        numOfEntities++;

        // update the caches
        uniqueName = entity.getString(GenericHelper.UNIQUE_NAME_PATH);
        entityID2DN.put(uniqueId, normalizeDN(uniqueName));
        entityDN2DO.put(normalizeDN(uniqueName), entity);

        if (logEnabled) {
            trcLogger.logp(Level.FINER, CLASSNAME, METHODNAME,
                "Number of entities=" + numOfEntities);
        }

        // save the entity in the file repository
        saveEntities();

        // build the return root dataobject containing the enity with its
        // identifier
        returnRoot = SDOHelper.createRootDataObject();
        DataObject returnDO = returnRoot.createDataObject(DO_ENTITIES,
            entity.getType().getURI(), entityType);
        setEntityIdentifier(returnDO, uniqueName, uniqueId);

        if (logEnabled) {
            trcLogger.exiting(CLASSNAME, METHODNAME, "Added " + entityType
                + ":" + uniqueName);
        }
        return returnRoot;
    }

    /**
     * @see AbstractAdapterImpl#addMemberToGroup(String, String)
     */
    public void addMemberToGroup(String groupUniqueName,
        String memberUniqueName) throws WIMException
    {
        final String METHODNAME = "addMemberToGroup";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME, "adding member "
                + memberUniqueName + " to group " + groupUniqueName);
        }

        // get the group entity from repository
        DataObject group = getEntityByUniqueName(groupUniqueName);

        // check if the entity is already a member, if yes, do nothing
        boolean memberExists = checkGroupMembership(group, memberUniqueName,
            PROP_LEVEL_IMMEDIATE, INITIAL_CURRENT_LEVEL);

        // add this member to the group
        if (!memberExists) {
            DataObject memberDO = getEntityByUniqueName(memberUniqueName);
            
            // copy member identifier to group entity dataobject
            GenericHelper.copyIdentifierDataObject(
                group.createDataObject(DO_MEMBERS), memberDO);

            // set the modify timestamp for the group
            group.set(PROP_MODIFY_TIMESTAMP, getDateString());

            // save the entity in the file repository
            saveEntities();
        }

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.exiting(CLASSNAME, METHODNAME, "member added="
                + (!memberExists));
        }
    }

    /**
     * @see AbstractAdapterImpl#getEntity(DataObject, DataObject, DataObject)
     */
    public DataObject getEntity(DataObject inEntity, DataObject propertyCtrl,
        DataObject returnRoot) throws WIMException
    {
        final String METHODNAME = "getEntity";

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, METHODNAME);
        }

        // get the uniqueName from the input entity dataobject
        String uniqueName = getUniqueName(inEntity);

        // get the entity from the repository
        DataObject entityDO = getEntityByUniqueName(uniqueName);

        // get the real entity type and create the return entity dataobject
        // inside the returnRoot
        String returnType = entityDO.getType().getName();
        DataObject returnEntity = returnRoot.createDataObject(DO_ENTITIES,
            entityDO.getType().getURI(), returnType);

        // get the properties to be returned from the PropertyControl
        List entityProps = null;
        if (propertyCtrl != null) {
            entityProps = propertyCtrl.getList(PROP_PROPERTIES);
        }

        // copy the entity's identifier and requested properties
        // to the return entity
        GenericHelper.copyDataObject(returnEntity, entityDO, entityProps,
            GenericHelper.IDENTIFIER_REF, mappedPrincipalNameProperty,
            mappedPrincipalNamePropertyMultiValued, repositoryId);

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, METHODNAME);
        }

        // return the entity, returnRoot will also contain this entity
        return returnEntity;
    }

    /**
     * @see AbstractAdapterImpl#getGroupMembers(DataObject, DataObject)
     */
    public void getGroupMembers(DataObject inGrpEntity,
        DataObject grpMemberCtrl) throws WIMException
    {
        // get the nesting level
        int level = grpMemberCtrl.getInt(PROP_LEVEL);

        // get the member properties to be returned
        List mbrProps = grpMemberCtrl.getList(PROP_PROPERTIES);

        // initialize the XPath helper to find the matching group members
        XPathHelper xpathHelper = new XPathHelper(grpMemberCtrl,
            loginProperties, loginPropertiesTypeMultiValued);

        // get the group members and update the input grpEntity with its
        // "member"
        DataObject grpEntity=getEntityByUniqueName(getUniqueName(inGrpEntity));
        getGroupMembers(grpEntity, inGrpEntity, mbrProps, level,
            INITIAL_CURRENT_LEVEL, xpathHelper, new HashSet());
    }

    /**
     * @see AbstractAdapterImpl#getGroupMembership(DataObject, DataObject)
     */
    public void getGroupMembership(DataObject mbrEntity,
        DataObject grpMembershipCtrl) throws WIMException
    {
        // get the nesting level
        int level = grpMembershipCtrl.getInt(PROP_LEVEL);

        // get the group properties to be returned
        List grpProps = grpMembershipCtrl.getList(PROP_PROPERTIES);

        // initialize the XPath helper to find the matching groups
        XPathHelper xpathHelper = new XPathHelper(grpMembershipCtrl,
            loginProperties, loginPropertiesTypeMultiValued);

        // get the groups this member belongs to and update the member entity
        // with its "groups"
        getGroupMembership(getUniqueName(mbrEntity), mbrEntity, grpProps,
            level, INITIAL_CURRENT_LEVEL, xpathHelper, new HashSet());
    }

    /**
     * @see AbstractAdapterImpl#checkGroupMembership(DataObject, DataObject,
     *      DataObject)
     */
    public void checkGroupMembership(DataObject entity,
        DataObject returnRoot, DataObject checkGrpMbrshipCtrl)
        throws WIMException
    {
        final String METHODNAME = "checkGroupMembership";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME);
        }

        // get the nesting level
        int level = checkGrpMbrshipCtrl.getInt(PROP_LEVEL);

        boolean inGroup = false;
        String groupDN = null;
        String memberDN = null;

        // if entity is a group and member is specified as "members" element
        if (SchemaHelper.isGroupType(entity.getType().getName())) {
            // get the group uniqueName
            groupDN = getUniqueName(entity);

            // get the member uniqueName
            memberDN = getUniqueName(entity.getDataObject(DO_MEMBERS + ".0"));
        }
        else {
            // entity is a member and group is specified as "groups" element
            // get the member uniqueName
            memberDN = getUniqueName(entity);

            // get the group uniqueName
            groupDN = getUniqueName(entity.getDataObject(DO_GROUPS + ".0"));
        }

        // group and member uniqueName are determined
        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME,
                "group uniqueName=" + groupDN + ", member uniqueName="
                    + memberDN);
        }

        // validate that the group and member are valid
        DataObject groupDO = getEntityByUniqueName(groupDN);
        getEntityByUniqueName(memberDN);

        // check if the member is in the group
        inGroup = checkGroupMembership(groupDO, memberDN, level,
            INITIAL_CURRENT_LEVEL);

        // set the result in CheckGroupMembershipControl
        DataObject rspCtrl = returnRoot.createDataObject(DO_CONTROLS,
            WIM_NS_URI, DO_CHECK_GROUP_MEMBERSHIP_CONTROL);
        rspCtrl.setBoolean(PROP_IN_GROUP, inGroup);

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.exiting(CLASSNAME, METHODNAME, "inGroup=" + inGroup);
        }
    }

    /**
     * @see AbstractAdapterImpl#deleteEntities(DataObject)
     */
    public DataObject deleteEntities(DataObject root) throws WIMException
    {
        final String METHODNAME = "deleteEntities";

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, METHODNAME);
        }

        // create a root dataobject to be returned
        DataObject returnRoot = SDOHelper.createRootDataObject();
        String uniqueName = null;

        try {
            /*
             * This adapter does not support OrgContainer, so delete descendant
             * is not supported. But here is the sample code to get that. 
             * <code> 
             *  // get the delete control dataobject 
             *  Map ctrlMap = ControlsHelper.getControlMap(root); 
             *  DataObject deleteCtrl = 
             *      (DataObject) ctrlMap.get(DO_DELETE_CONTROL); 
             *  // get the flag, whether descendants needs to be deleted 
             *  boolean delDesc = (deleteCtrl != null) ? deleteCtrl.getBoolean(
             *     PROP_DELETE_DESCENDANTS) : false; 
             * </code>
             */

            // get all the entities to be deleted
            List entities = root.getList(DO_ENTITIES);
            Map deleted = new HashMap();

            // delete one entity at a time and clean up its references
            for (int i = 0; i < entities.size(); i++) {
                DataObject entity = (DataObject) entities.get(i);
                uniqueName = getUniqueName(entity);

                // delete the entity from repository
                List deletedEntityData = deleteEntity(uniqueName);
                if (deletedEntityData != null) {
                    deleted.put(uniqueName, deletedEntityData);
                }

                // cleanup references to deleted entity to maintain referential
                // integrity
                cleanReferences(null, uniqueName);
            }

            // save the updated data in file repository
            saveEntities();

            //add the deleted entities identifiers to the returnRoot dataobject
            for (Iterator iter = deleted.keySet().iterator(); iter.hasNext();) 
            {
                String deletedUniqueName = (String) iter.next();
                List deletedEntityData = 
                    (List) deleted.get(deletedUniqueName);

                // get the entity type and uniqueId of the deleted entity
                String entityType = (String) deletedEntityData.get(0);
                String deletedUniqueId = (String) deletedEntityData.get(1);

                // set the entity identifier for deleted entity
                DataObject returnDO = GenericHelper.createDataObject(
                    returnRoot, DO_ENTITIES, entityType);
                setEntityIdentifier(returnDO, deletedUniqueName,
                    deletedUniqueId);
            }
        }
        catch (WIMException we) {
            throw we;
        }
        catch (Exception e) {
            throw new RemoveEntityException(
                WIMMessageKey.ENTITY_DELETE_FAILED, 
                WIMMessageHelper.generateMsgParms(uniqueName, e.getMessage()),
                Level.SEVERE, CLASSNAME, METHODNAME, e);
        }

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, METHODNAME);
        }
        return returnRoot;
    }

    /**
     * @see AbstractAdapterImpl#updateEntity(DataObject, String, List,
     *      DataObject)
     */
    public void updateEntity(DataObject updatedEntity, String uniqueName,
        List modItems) throws WIMException
    {
        final String METHODNAME = "updateEntity";

        boolean logEnabled = trcLogger.isLoggable(Level.FINEST);
        if (logEnabled) {
            trcLogger.logp(Level.FINER, CLASSNAME, METHODNAME,
                "update entry " + uniqueName + " with attributes " + modItems);
        }

        // get the entity dataobject to be updated from the repository
        DataObject entity = getEntityByUniqueName(uniqueName);

        try {
            // for each properties in the modification list:
            // add, remove, or replace depending on the modification operation
            for (int i = 0; i < modItems.size(); i++) {
                // get the modified item
                ModificationItem modItem = (ModificationItem) modItems.get(i);

                // get the modification operation
                int modOp = modItem.getModificationOp();

                // get the property name which has been modified
                Attribute attr = modItem.getAttribute();
                String propName = attr.getID();

                // if the modification operation is replace or remove,
                // remove the value first
                if ((modOp == DirContext.REPLACE_ATTRIBUTE) || 
                    (modOp == DirContext.REMOVE_ATTRIBUTE)) {
                    if (logEnabled) {
                        trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME,
                            "removing property:" + propName);
                    }
                    entity.unset(propName);
                }
                // if the operation is replace or add, set the new value(s)
                if ((modOp == DirContext.REPLACE_ATTRIBUTE) || 
                    (modOp == DirContext.ADD_ATTRIBUTE)) {
                    if (logEnabled) {
                        trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME,
                            "adding property:" + propName);
                    }

                    Object value = attr.get();

                    // if the property is multivalued
                    if (value instanceof List) {
                        List values = (List) value;

                        for (int j = 0; j < values.size(); j++) {
                            // if the value is a dataobject, 
                            // copy all the values from dataobject
                            if ((values.get(j) instanceof DataObject)) {
                                DataObject copyDO = 
                                    entity.createDataObject(propName);
                                GenericHelper.copyAllPropsOfDataObject(copyDO,
                                    (DataObject) values.get(j));
                            }
                            else {
                                // set the multivalued property
                                entity.getList(propName).add(values.get(j));
                            }
                        }
                    }
                    else {
                        // set the singlevalued property
                        if (PROP_PASSWORD.equals(propName)) {
                            entity.set(propName, hash((byte[]) value));
                        }
                        else {
                            entity.set(propName, value);
                        }
                    }
                }
            }
        }
        catch (WIMException we) {
            throw we;
        }
        catch (Exception e) {
            throw new WIMApplicationException(WIMMessageKey.GENERIC, 
                WIMMessageHelper.generateMsgParms(e.getMessage()), 
                Level.SEVERE, CLASSNAME, METHODNAME, e);
        }

        // set the modify timestamp for the entity and save it
        entity.set(PROP_MODIFY_TIMESTAMP, getDateString());
        saveEntities();

        if (logEnabled) {
            trcLogger.exiting(CLASSNAME, METHODNAME);
        }
    }

    /**
     * @see AbstractAdapterImpl#searchEntities(DataObject)
     */
    public DataObject searchEntities(DataObject searchCtrl)
        throws WIMException
    {
        final String METHODNAME = "searchEntities";

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, METHODNAME);
        }

        DataObject returnRoot = null;
        try {
            // get the properties to be returned for matching entities
            List returnProps = searchCtrl.getList(PROP_PROPERTIES);

            // whether to return subtypes or not
            boolean returnSubType = 
                searchCtrl.getBoolean(PROP_RETURN_SUB_TYPE);

            // parse the XPATH search expression and build a tree
            XPathHelper xpathHelper = new XPathHelper(searchCtrl,
                loginProperties, loginPropertiesTypeMultiValued);
            List entityTypes = xpathHelper.getEntityTypes();

            Collection resultDOs = null;

            // search the repository
            resultDOs = search(entityTypes, xpathHelper, returnSubType);

            // construct the return root dataobject
            returnRoot = SDOHelper.createRootDataObject();

            // for each matching entity, copy the properties to be returned
            Iterator itr = resultDOs.iterator();
            while (itr.hasNext()) {
                DataObject srcEntityDO = (DataObject) itr.next();
                EClass entityEClass = 
                    SchemaHelper.getEClass(srcEntityDO.getType());

                // make a copy of the entity dataobject
                DataObject destEntityDO = 
                    (DataObject) EcoreUtil.create(entityEClass);

                // copy the requested entity properties
                GenericHelper.copyDataObject(destEntityDO, srcEntityDO,
                    returnProps, GenericHelper.IDENTIFIER_REF,
                    mappedPrincipalNameProperty,
                    mappedPrincipalNamePropertyMultiValued, repositoryId);

                // add it to the return list
                returnRoot.getList(DO_ENTITIES).add(destEntityDO);
            }
        }
        catch (WIMException we) {
            throw we;
        }
        catch (Exception e) {
            throw new WIMApplicationException(
                WIMMessageKey.ENTITY_SEARCH_FAILED, 
                WIMMessageHelper.generateMsgParms(e.getMessage()), 
                Level.SEVERE, CLASSNAME, METHODNAME, e);
        }
        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, METHODNAME);
        }
        return returnRoot;
    }

    /**
     * @see AbstractAdapterImpl#login(DataObject, DataObject)
     */
    public DataObject login(DataObject account, DataObject loginCtrl)
        throws WIMException
    {
        final String METHODNAME = "login";

        boolean logEnabled = trcLogger.isLoggable(Level.FINER);
        if (logEnabled) {
            trcLogger.entering(CLASSNAME, METHODNAME);
        }

        DataObject returnRoot = null;
        DataObject accountDO = null;
        String loginProp = null;
        String principalName = null;

        try {
            // login by certificates not supported by this adapter
            if (account.isSet(PROP_CERTIFICATE)) {
                throw new CertificateMapNotSupportedException(
                    WIMMessageKey.AUTHENTICATION_WITH_CERT_NOT_SUPPORTED,
                    WIMMessageHelper.generateMsgParms(repositoryId),
                    CLASSNAME, METHODNAME);
            }

            // get the principalName and password
            principalName = account.getString(PROP_PRINCIPAL_NAME);
            byte[] password = account.getBytes(PROP_PASSWORD);

            if (logEnabled) {
                trcLogger.logp(Level.FINER, CLASSNAME, METHODNAME,
                    "principalName=" + principalName);
            }

            // login without principalName is not supported
            // login with uniqueName/uniqueId is not supported
            // uniqueName could be in the identifier or 
            // set as the principalName
            // some adapters may support login by uniqueName/uniqueId
            if ((principalName == null) || 
                (principalName.trim().length() == 0)) {
                throw new PasswordCheckFailedException(
                    WIMMessageKey.MISSING_OR_EMPTY_PRINCIPAL_NAME, CLASSNAME,
                    METHODNAME);
            }
            else if (GenericHelper.isDN(principalName)) {
                throw new PasswordCheckFailedException(
                    "Login with uniqueName is not supported: "
                        + principalName, CLASSNAME, METHODNAME);
            }

            // login without password is not allowed
            if ((password == null) || (password.length == 0)) {
                throw new PasswordCheckFailedException(
                    WIMMessageKey.MISSING_OR_EMPTY_PASSWORD, null,
                    Level.WARNING, CLASSNAME, METHODNAME);
            }

            String DN = null;
            String accountEntityType = account.getType().getName();
            String uri = account.getType().getURI();

            // build the search expression to search for the matching entity
            String searchExpr = "//entities[@xsi:type='" + accountEntityType
                + "' and " + PROP_PRINCIPAL_NAME + "='" + principalName
                + "']";

            // get search base from LoginControl, ignore mappingProperties
            List searchBases = loginCtrl.getList(PROP_SEARCH_BASES);

            XPathHelper xpathHelper = new XPathHelper(searchExpr,
                searchBases, loginProperties, loginPropertiesTypeMultiValued);

            // search for the matching account
            List entityTypes = new Vector();
            entityTypes.add(accountEntityType);
            List accountList = search(entityTypes, xpathHelper, true);

            if (accountList.size() == 1) {
                // a matching account was found
                accountDO = (DataObject) accountList.get(0);
            }
            else if (accountList.size() > 1) {
                /// more than one matching accounts found, throw exception
                throw new PasswordCheckFailedException(
                    WIMMessageKey.MULTIPLE_PRINCIPALS_FOUND, 
                    WIMMessageHelper.generateMsgParms(principalName),
                    CLASSNAME, METHODNAME);
            }

            // one and only one matching account was found
            if (accountDO != null) {
                // check the password
                if (checkPassword(password, 
                    accountDO.getBytes(PROP_PASSWORD))) {
                    // get the account properties to be returned
                    List accountProps = loginCtrl.getList(PROP_PROPERTIES);

                    // return the PersonAccount props
                    returnRoot = SDOHelper.createRootDataObject();
                    DataObject returnAccountDO = returnRoot.createDataObject(
                        DO_ENTITIES, uri, accountDO.getType().getName());

                    // copy the props and identifiers to the output entity
                    GenericHelper.copyDataObject(returnAccountDO, accountDO,
                        accountProps, GenericHelper.IDENTIFIER_REF,
                        mappedPrincipalNameProperty,
                        mappedPrincipalNamePropertyMultiValued, repositoryId);
                    if (principalName != null) {
                        // set the principalName to incoming principalName
                        returnAccountDO.setString(PROP_PRINCIPAL_NAME,
                            principalName);
                    }
                }
                else {
                    // password did not match
                    throw new PasswordCheckFailedException(
                        WIMMessageKey.PASSWORD_MATCH_FAILED, null,
                        Level.WARNING, CLASSNAME, METHODNAME);
                }
            }
            else {
                // account not found in file repository,
                // return empty dataobject, profileManager will throw
                // PasswordCheckFailedException with PRINCIPAL_NOT_FOUND
                // message if account is not found in any repository
                if (logEnabled) {
                    trcLogger.logp(Level.FINER, CLASSNAME, METHODNAME,
                        "principal, " + principalName + ", not found in "
                            + repositoryId);
                }
                returnRoot = SDOHelper.createRootDataObject();
            }
        }
        catch (WIMException we) {
            throw we;
        }
        catch (Exception ex) {
            if (logEnabled) {
                trcLogger.logp(Level.FINER, CLASSNAME, METHODNAME,
                    "Login failed", ex);
            }
            throw new WIMApplicationException(
                WIMMessageKey.PASSWORD_CHECKED_FAILED, 
                WIMMessageHelper.generateMsgParms(
                    principalName, ex.getMessage()),
                Level.SEVERE, CLASSNAME, METHODNAME, ex);
        }

        if (logEnabled) {
            trcLogger.exiting(CLASSNAME, METHODNAME);
        }

        return returnRoot;
    }

    /**
     * File repository supports all the entities and all their properties
     * defined in the schema. If your adapter supports selected entity types and
     * properties, then return those entity types and properties in this method.
     * 
     * @see AbstractAdapterImpl#getSchema(DataObject, DataObject, DataObject)
     */
    public DataObject getSchema(DataObject dataTypeCtrl,
        DataObject propDefCtrl, DataObject entityTypeCtrl)
        throws WIMException
    {
        final String METHODNAME = "getSchema";

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, METHODNAME);
        }

        // create the return root
        DataObject returnRoot = SDOHelper.createRootDataObject();

        // get/create the schema dataobject
        DataObject schema = returnRoot.getDataObject(DO_SCHEMA);
        if (schema == null) {
            schema = returnRoot.createDataObject(DO_SCHEMA);
        }

        // build the list of supported entity types
        List entityTypeList = new ArrayList();
        entityTypeList.add(DO_PERSON_ACCOUNT);
        entityTypeList.add(DO_GROUP);

        // check if we have a data type control
        if (dataTypeCtrl != null) {
            // get the current data type list
            List dtList = schema.getList(DO_PROPERTY_DATA_TYPES);

            for (Iterator entityTypeIterator = entityTypeList.iterator(); 
                entityTypeIterator.hasNext();) {
                String entityName = (String) entityTypeIterator.next();

                // get all the properties defined for the entity type
                List propList = SchemaHelper.getProperties(entityName);
                for (int i = 0; i < propList.size(); i++) {
                    Property prop = (Property) propList.get(i);
                    String tName = prop.getType().getName();

                    // add the property name to the set and to the schema
                    if (!dtList.contains(tName)) {
                        dtList.add(tName);
                    }
                }
            }
        }
        else if (entityTypeCtrl != null) {
            // retrieve the entity definition

            // get the list of entity types from the control dataobject
            List reqEntTypes = entityTypeCtrl.getList(PROP_ENTITY_TYPE_NAMES);

            if (reqEntTypes == null || reqEntTypes.size() == 0) {
                // no specific entity type was requested
                // retrieve and return all supported entity types
                reqEntTypes = entityTypeList;
            }

            for (int i = 0; i < reqEntTypes.size(); i++) {
                String reqEntType = (String) reqEntTypes.get(i);
                if (entityTypeList.contains(reqEntType)) {
                    addEntityToSchemaDO(schema, reqEntType);
                }
                else {
                    // entity type is not valid, skip this
                    if (trcLogger.isLoggable(Level.FINE)) {
                        trcLogger.logp(Level.FINE, CLASSNAME, METHODNAME,
                            "The entity type " + reqEntType
                                + " is not supported in repository "
                                + repositoryId);
                    }
                }
            }
        }
        else if (propDefCtrl != null) {
            // retrieve property definition

            // get the entity type name and property names from control
            // dataobject
            String entityTypeName = 
                propDefCtrl.getString(PROP_ENTITY_TYPE_NAME);
            List pNames = propDefCtrl.getList(PROP_PROPERTY_NAMES);

            if (pNames == null || pNames.size() == 0) {
                // no specific property was requested
                // return all properties for the specified entity type
                if (entityTypeList.contains(entityTypeName)) {
                    List properties = 
                        SchemaHelper.getProperties(entityTypeName);
                    for (int i = 0; ((properties != null) && 
                        (i < properties.size())); i++) {
                        Property property = (Property) properties.get(i);
                        addPropertyToSchemaDO(schema, property.getName());
                    }
                }
            }
            else {
                // only return the schema for requested property names
                for (int i = 0; i < pNames.size(); i++) {
                    addPropertyToSchemaDO(schema, (String) pNames.get(i));
                }
            }
        }

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, METHODNAME);
        }
        return returnRoot;
    }

    /**
     * @see AbstractAdapterImpl#rename(String, DataObject, String, String)
     */
    public void rename(String entityType, DataObject updatedEntity,
        String uniqueName, String newUniqueName) throws WIMException
    {
        final String METHODNAME = "rename";

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, METHODNAME, "uniqueName:"
                + uniqueName + " #newUniqueName:" + newUniqueName);
        }

        // validate the old and new uniqueNames
        if (uniqueName == null || newUniqueName == null) {
            throw new InvalidArgumentException(
                WIMMessageKey.ENTITY_IDENTIFIER_NOT_SPECIFIED, null,
                Level.SEVERE, CLASSNAME, METHODNAME);
        }

        if (entityAlreadyExists(null, newUniqueName)) {
            throw new EntityAlreadyExistsException(
                WIMMessageKey.ENTITY_ALREADY_EXIST, 
                WIMMessageHelper.generateMsgParms(newUniqueName), 
                Level.SEVERE, CLASSNAME, METHODNAME);
        }

        /*
         * If the adapter supports OrgContainer and if an OrgContainer is being
         * renamed, then make sure that there are no descendants to this
         * orgContainer. Here is the sample code: 
         * <code> 
         * if (AdapterUtils.isSuperType(DO_ORGCONTAINER, entityType)) { 
         *   // if the org has decendants 
         *   // implementation of getImmediateDescendants() is not provided 
         *   if (getImmediateDescendants(uniqueName).size() > 0) {
         *      throw new EntityHasDescendantsException(
         *         WIMMessageKey.ENTITY_HAS_DESCENDENTS,
         *         WIMMessageHelper.generateMsgParms(uniqueName), 
         *         Level.SEVERE, CLASSNAME, METHODNAME); 
         *   }
         * }
         * </code>
         */

        // retrieve the entity from the repository
        DataObject entity = getEntityByUniqueName(uniqueName);

        // rename the entity: set the uniqueName to new uniqueName
        // the RDN Property will be updated when updateEntity() is called
        DataObject id = entity.getDataObject(DO_IDENTIFIER);
        id.set(PROP_UNIQUE_NAME, newUniqueName);
        id.set(PROP_EXTERNAL_NAME, newUniqueName);

        // set the modify timestamp
        entity.set(PROP_MODIFY_TIMESTAMP, getDateString());

        // update the caches
        entityDN2DO.remove(normalizeDN(uniqueName));
        entityDN2DO.put(normalizeDN(newUniqueName), entity);
        String uniqueId = getEntityID(entity);
        entityID2DN.put(uniqueId, normalizeDN(newUniqueName));

        // change any references to the old uniqueName to use new uniqueName
        changeReferences(uniqueName, newUniqueName);

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, METHODNAME);
        }
    }

    /**
     * @see AbstractAdapterImpl#updateGroupMembers(DataObject, String, List,
     *      int, DataObject)
     */
    public void updateGroupMembers(DataObject entity, String groupUniqueName,
        List memberDNs, int grpMbrMod) throws WIMException
    {
        DataObject group = getEntityByUniqueName(groupUniqueName);

        // if mode is replace, remove all the current members
        if (grpMbrMod == VALUE_MODIFY_MODE_REPLACE) {
            // remove the member from current groups
            group.unset(DO_MEMBERS);
        }

        // if mode is add or replace, add the new members
        if ((grpMbrMod == VALUE_MODIFY_MODE_ASSIGN) || 
            (grpMbrMod == VALUE_MODIFY_MODE_REPLACE)) {
            for (int i = 0; i < memberDNs.size(); i++) {
                addMemberToGroup(groupUniqueName, (String) memberDNs.get(i));
            }
        }
        else if (grpMbrMod == VALUE_MODIFY_MODE_UNASSIGN) {
            // mode is unassign: remove the specified members
            for (int i = 0; i < memberDNs.size(); i++) {
                removeMemberFromGroup(groupUniqueName, 
                    (String) memberDNs.get(i));
            }
        }

        // save the data in file repository
        saveEntities();
    }

    /**
     * @see AbstractAdapterImpl#updateGroupMembership(DataObject, String, List,
     *      int, DataObject)
     */
    public void updateGroupMembership(DataObject entity,
        String memberUniqueName, List groupUniqueNames, int grpMbrshipMod)
        throws WIMException
    {
        DataObject member = getEntityByUniqueName(memberUniqueName);

        // if mode is replace
        if (grpMbrshipMod == VALUE_MODIFY_MODE_REPLACE) {
            // get the groups of the member
            List currentGroupDNs = getGroupsForEntity(memberUniqueName, null);
            if (currentGroupDNs != null) {
                // remove the member from current groups
                for (int i = 0; i < currentGroupDNs.size(); i++) {
                    removeMemberFromGroup((String) currentGroupDNs.get(i),
                        memberUniqueName);
                }
            }
        }

        // if mode is add or replace, add the member to new groups
        if ((grpMbrshipMod == VALUE_MODIFY_MODE_ASSIGN)
            || (grpMbrshipMod == VALUE_MODIFY_MODE_REPLACE)) {
            for (int i = 0; i < groupUniqueNames.size(); i++) {
                addMemberToGroup((String) groupUniqueNames.get(i),
                    memberUniqueName);
            }
        }
        else if (grpMbrshipMod == VALUE_MODIFY_MODE_UNASSIGN) {
            // remove member from specified groups
            for (int i = 0; i < groupUniqueNames.size(); i++) {
                removeMemberFromGroup((String) groupUniqueNames.get(i),
                    memberUniqueName);
            }
        }

        // save the data in file repository
        saveEntities();
    }

    //---------------------------------------------------------------
    // P R I V A T E    M E T H O D S
    //---------------------------------------------------------------

    /**
     * Initialize the supported entity reference properties.
     */
    private void initReferenceProperties() {
        // group has multiValued "members" property
        Map groupRef = new HashMap(1);
        groupRef.put(DO_MEMBERS, ENTITY_TYPE);
        entityReference.put(DO_GROUP, groupRef);

        // PersonAccount has multiValued "manager" and "secretary" properties
        Map personRef = new HashMap(2);
        personRef.put("manager", IDENTIFIER_TYPE);
        personRef.put("secretary", IDENTIFIER_TYPE);
        entityReference.put(DO_PERSON_ACCOUNT, personRef);
    }

    /**
     * Validate a file name.
     * 
     * @param fileName file name to be validated.
     * 
     * @return true if file name is valid.
     */
    private boolean isValidFileName(String fileName)
    {
        try {
            if (fileName != null && fileName.trim().length() > 0) {
                new File(fileName);
                return true;
            }
        }
        catch (Exception e) {
            trcLogger.logp(Level.FINER, CLASSNAME, "isValidFileName",
                "checking for file: " + fileName, e);
        }
        return false;
    }

    /**
     * Load the entities defined in the file repository.
     * 
     * @throws WIMException
     */
    private synchronized void loadFileData() throws WIMException
    {
        final String METHODNAME = "loadFileData";

        boolean logEnabled = trcLogger.isLoggable(Level.FINER);
        if (logEnabled) {
            trcLogger.entering(CLASSNAME, METHODNAME);
        }

        // load all the entities from file
        try {
            // load the data from file
            entityDG = loadFileAsDataGraph(fileName);
        }
        catch (FileNotFoundException e) {
            // file was not found there may not be any data
            trcLogger.logp(Level.FINER, CLASSNAME, METHODNAME, e.getMessage());
        }
        catch (Exception e) {
            throw new InitializationException(
                WIMMessageKey.ERROR_READING_FILE, 
                WIMMessageHelper.generateMsgParms(fileName, e.getMessage()),
                Level.SEVERE, CLASSNAME, METHODNAME, e);
        }

        // if there was some data in the file, count the number of entities
        // and initialize the mapping caches
        if (entityDG != null) {
            // get the number of items in the datagraph
            entityRoot = entityDG.getRootObject().getDataObject(DO_ROOT);
            List entities = entityRoot.getList(DO_ENTITIES);
            numOfEntities = entities.size();
            if (logEnabled) {
                trcLogger.logp(Level.FINER, CLASSNAME, METHODNAME,
                    "Number of entities=" + numOfEntities);
            }

            // initialize the mapping caches
            for (int j = 0; j < numOfEntities; j++) {
                DataObject entity = (DataObject) entities.get(j);
                String ID = getEntityID(entity);
                String DN = normalizeDN(
                    entity.getString(GenericHelper.UNIQUE_NAME_PATH));
                entityID2DN.put(ID, DN);
                entityDN2DO.put(DN, entity);
            }

            if (trcLogger.isLoggable(Level.FINEST)) {
                trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME,
                    "Data in File repository=\n"
                        + WIMTraceHelper.printDataGraph(entityDG));
            }
        }
        else {
            if (logEnabled) {
                trcLogger.logp(Level.FINER, CLASSNAME, METHODNAME,
                    "No data exists in the file repository or "
                        + " root dataobject is empty.");
            }
        }

        if (logEnabled) {
            trcLogger.exiting(CLASSNAME, METHODNAME);
        }
    }

    /**
     * Load an XML file as a DataGraph.
     * 
     * @param fileName name of the file containing serialized SDO dataobject.
     * @return the datagraph
     */
    private DataGraph loadFileAsDataGraph(String fileName) throws Exception
    {
        final String METHODNAME = "loadFileAsDataGraph";

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.logp(Level.FINER, CLASSNAME, METHODNAME,
                "Loading from " + fileName);
        }

        // use the SDO utility to load the file
        DataGraph outDG = null;
        HashMap options = new HashMap();
        options.put(XMLResource.OPTION_EXTENDED_META_DATA,
            ExtendedMetaData.INSTANCE);

        FileInputStream inputStream = new FileInputStream(fileName);
        outDG = SDOUtil.loadDataGraph(inputStream, options);
        inputStream.close();

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.logp(Level.FINER, CLASSNAME, METHODNAME, "Loaded from "
                + fileName);
            trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME,
                "Loaded Data:" + outDG);
        }

        return outDG;
    }

    /**
     * Save the current entities to the file repository.
     * 
     * @throws WIMException
     */
    private synchronized void saveEntities() throws WIMException {
        GenericHelper.saveDataGraphToFile(entityDG, fileName);
    }

    /**
     * Return the lowercase distinguished name for case-insensitive comparisons
     */
    private String normalizeDN(String DN)
    {
        return (DN != null) ? DN.toLowerCase() : null;
    }

    /**
     * Do a case insensitive comparison and return true if two strings are
     * equal.
     * 
     * @param str1 a string (non-null)
     * @param str2 another string
     */
    private boolean normalizedStringsAreEqual(String str1, String str2)
    {
        return str1.equalsIgnoreCase(str2);
    }

    /**
     * Do a case insensitive comparison and return true if key is contained in
     * the map. Map contains the keys in lower case.
     * 
     * @param map a map containing string as key (non-null)
     * @param key another key string
     */
    public boolean containsNormalizedKey(Map map, String key)
    {
        return map.containsKey(key.toLowerCase());
    }

    /**
     * Get the externalId or uniqueId of the entity. If externalId is not
     * present, get the uniqueId.
     */
    private String getEntityID(DataObject entity)
    {
        String ID = entity.getString(GenericHelper.EXTERNAL_ID_PATH);
        if (ID == null) {
            ID = entity.getString(GenericHelper.UNIQUE_ID_PATH);
        }
        return ID;
    }

    /**
     * Hash the password. For this sample adapter, this method does not
     * actually do any real hashing. If a repository does not store passwords
     * in hashed/encrypted form then it might be needed to provide a real 
     * hashing implementation of this method.
     * 
     * @param password password to be hashed
     */
    private byte[] hash(byte[] password)
    {
        StringBuffer finalStr = new StringBuffer();
        finalStr.append("hashedPassword:");
        finalStr.append(new String(password));
        return finalStr.toString().getBytes();
    }

    /**
     * Return the date string to be set in the dataobject.
     */
    private String getDateString()
    {
        final SimpleDateFormat sdf = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ");

        StringBuffer date = new StringBuffer(sdf.format(new Date()));
        // above string is in this format
        //    2005-05-04T09:34:18.444-0400
        // convert it to
        //    2005-05-04T09:34:18.444-04:00
        date.insert(date.length() - 2, ":");
        return date.toString();
    }

    /**
     * Set the identifier properties of an entity in the return entity
     * dataobject.
     * 
     * @param returnDO entity dataobject to be returned by the adapter
     * @param uniqueName uniqueName of the entity
     * @param uniqueId uniqueId of the entity
     */
    private void setEntityIdentifier(DataObject returnDO, String uniqueName,
        String uniqueId)
    {
        DataObject idDO = returnDO.createDataObject(DO_IDENTIFIER);
        idDO.setString(PROP_UNIQUE_NAME, uniqueName);
        idDO.setString(PROP_EXTERNAL_NAME, uniqueName);
        idDO.setString(PROP_UNIQUE_ID, uniqueId);
        idDO.setString(PROP_EXTERNAL_ID, uniqueId);
        idDO.setString(PROP_REPOSITORY_ID, repositoryId);
    }

    /**
     * Return the entity dataobject for the specified uniqueName. Some
     * repositories may not support retrieving the entity with uniqueName, in
     * that case the uniqueName can be parsed to determine the property and
     * value. For example, <br>
     * uid=abcd,o=myorg : propName=uid, value=abcd ==> probably an user <br>
     * cn=abcd,o=myorg : propName=cn, value=abcd ==> probably a group. <br>
     * Entity type in the input dataobject can also be used but it may not
     * always be set.
     * 
     * @throws EntityNotFoundException
     */
    private DataObject getEntityByUniqueName(String uniqueName)
        throws EntityNotFoundException
    {
        // retrieve the entity from the cache
        DataObject entity = 
            (DataObject) entityDN2DO.get(normalizeDN(uniqueName));
        if (entity == null) {
            throw new EntityNotFoundException(WIMMessageKey.ENTITY_NOT_FOUND,
                WIMMessageHelper.generateMsgParms(uniqueName), CLASSNAME,
                "getEntityByUniqueName");
        }
        return entity;
    }

    /**
     * Returns the entity for the specified uniqueId.
     * 
     * @throws EntityNotFoundException
     */
    private DataObject getEntityByUniqueId(String uniqueId)
            throws EntityNotFoundException
    {
        // get the uniqueName for the uniqueId then get the entity
        return getEntityByUniqueName(getUniqueNameForUniqueId(uniqueId));
    }

    /**
     * Return the uniqueId for the specified uniqueName.
     * 
     * @throws EntityNotFoundException
     */
    private String getUniqueNameForUniqueId(String uniqueId)
        throws EntityNotFoundException
    {
        // retrieve the uniqueName from the cache
        String uniqueName = (String) entityID2DN.get(uniqueId);
        if (uniqueName == null) {
            throw new EntityNotFoundException(WIMMessageKey.ENTITY_NOT_FOUND,
                WIMMessageHelper.generateMsgParms(uniqueId), Level.FINE,
                CLASSNAME, "getUniqueNameForUniqueId");
        }
        return uniqueName;
    }

    /**
     * Returns true if the group contains the specified member. Membership is
     * evaluated at immediate level or at the nested level. If the level=0, then
     * this method is called recursively to check group membership at different
     * levels.
     * 
     * @param groupDO group dataobject
     * @param memberDN member's uniqueName
     * @param level nesting level, 0 means all levels
     * @param currentLevel current level
     *
     * @throws WIMException
     */
    private boolean checkGroupMembership(DataObject groupDO, String memberDN,
        int level, int currentLevel) throws WIMException
    {
        final String METHODNAME = "checkGroupMembership";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME, "currentLevel="
                + currentLevel + ", groupDN="
                + groupDO.getString(GenericHelper.UNIQUE_NAME_PATH));
        }

        int nextLevel = currentLevel + 1;
        boolean inGroup = false;

        // check if the groupDO is really a group
        if (SchemaHelper.isGroupType(groupDO.getType().getName())) {
            // check if the entity is a member of the group
            List members = groupDO.getList(DO_MEMBERS);

            // iterate thru all the members and compare the uniqueName
            for (int i = 0; i < members.size(); i++) {
                DataObject member = (DataObject) members.get(i);
                String mbrDN = member.getString(GenericHelper.UNIQUE_NAME_PATH);
                if (normalizedStringsAreEqual(mbrDN, memberDN)) {
                    inGroup = true;
                    break;
                }
            }

            // if member match is not found and need to check the nested groups
            if (!inGroup && 
                ((level == PROP_LEVEL_NESTED) || (nextLevel < level))) {
                // get the sub-groups of this group
                members = groupDO.getList(DO_MEMBERS);

                for (int i = 0; i < members.size(); i++) {
                    DataObject mbrIDDO = (DataObject) members.get(i);

                    // retrieve the member entity
                    DataObject memberDO = getEntityByUniqueName(
                        mbrIDDO.getString(GenericHelper.UNIQUE_NAME_PATH));

                    // if the member is a group then check the membership 
                    // inside the subgroup
                    if (SchemaHelper.isGroupType(memberDO.getType().getName())){
                        inGroup = (inGroup | checkGroupMembership(memberDO,
                            memberDN, level, nextLevel));
                    }
                }
            }
        }

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.exiting(CLASSNAME, METHODNAME, "inGroup=" + inGroup);
        }
        return inGroup;
    }

    /**
     * Get the members of a group with matching search expression. 
     * If level = 0, get the nested members of the group(members).
     * 
     * @param grpDO group dataobject whose members have to be returned
     * @param returnEntity return dataobject which will contain all the members
     *            of the group(s)
     * @param mbrProps properties of the member to be returned
     * @param level nesting level
     * @param currentLevel current level of nesting
     * @param xpathHelper XPath helper with parsed search expression
     * @param membersProcessed Set containing the members processed
     * 
     * @throws WIMException
     */
    private void getGroupMembers(DataObject grpDO, DataObject returnEntity,
        List mbrProps, int level, int currentLevel, XPathHelper xpathHelper,
        Set membersProcessed) throws WIMException
    {
        final String METHODNAME = "getGroupMembers";
        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, METHODNAME, "currentLevel="
                + currentLevel + ", for group: "
                + grpDO.getString(GenericHelper.UNIQUE_NAME_PATH));
        }

        // make sure entity is a group
        if (!SchemaHelper.isGroupType(grpDO.getType().getName())) {
            if (trcLogger.isLoggable(Level.FINER))
                trcLogger.exiting(CLASSNAME, METHODNAME,
                    "Entity is not a group:" + grpDO.getType().getName());
            return;
        }

        int nextLevel = currentLevel + 1;

        // for all the members of this group
        List members = grpDO.getList(DO_MEMBERS);
        for (int k = 0; k < members.size(); k++) {
            DataObject mbrIDDO = (DataObject) members.get(k);
            String mbrDN = mbrIDDO.getString(GenericHelper.UNIQUE_NAME_PATH);

            // if the member has already been processed, skip it
            if (membersProcessed.contains(mbrDN)) {
                continue;
            }
            else {
                membersProcessed.add(mbrDN);
            }

            // evaluate the member against the search expression and
            // add it to the return list if it matches
            boolean isMemberToCopy = false;
            DataObject memberDO = getEntityByUniqueName(mbrDN);
            if ((xpathHelper == null) || xpathHelper.evaluate(memberDO)) {
                isMemberToCopy = true;
                EClass mbrEClass = SchemaHelper.getEClass(memberDO.getType());
                DataObject returnMemberDO = 
                    (DataObject) EcoreUtil.create(mbrEClass);

                // copy the requested properties of the member
                GenericHelper.copyDataObject(returnMemberDO, memberDO,
                    mbrProps, GenericHelper.IDENTIFIER_REF,
                    mappedPrincipalNameProperty,
                    mappedPrincipalNamePropertyMultiValued, repositoryId);
                returnEntity.getList(DO_MEMBERS).add(returnMemberDO);
            }

            // get nested group members if the member is a group
            if (((level == PROP_LEVEL_NESTED) || (nextLevel < level)) && 
                (SchemaHelper.isGroupType(memberDO.getType().getName()))) {
                getGroupMembers(memberDO, returnEntity, mbrProps, level,
                    nextLevel, xpathHelper, membersProcessed);
                // if the tree view is to be returned
                //getGroupMembers(memberDO, returnMemberDO, mbrProps, level,
                // nextLevel, xpathHelper, membersProcessed);
            }
        }

        if (trcLogger.isLoggable(Level.FINER))
            trcLogger.exiting(CLASSNAME, METHODNAME);
    }

    /**
     * Get the groups (with matching search expression) a member belongs to. 
     * If level = 0, get the groups of the group.
     * 
     * @param mbrDN member uniqueName
     * @param returnEntity return dataobject which will contain the matching
     *            groups
     * @param grpProps properties of the group to be returned
     * @param level nesting level
     * @param currentLevel current level of nesting
     * @param xpathHelper XPath helper with parsed search expression
     * 
     * @param groupsProcessed Set containing the groups processed
     * 
     * @throws WIMException
     */
    private void getGroupMembership(String mbrDN, DataObject returnEntity,
        List grpProps, int level, int currentLevel, XPathHelper xpathHelper,
        Set groupsProcessed) throws WIMException
    {
        final String METHODNAME = "getGroupMembership";

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, METHODNAME, "currentLevel="
                + currentLevel + ", for member: " + mbrDN);
        }

        int nextLevel = currentLevel + 1;
        EClass grpEClass = SchemaHelper.getEClass(DO_GROUP);

        // get the groups of the entity with matching search expression
        List groupDNs = getGroupsForEntity(mbrDN, xpathHelper);
        for (int g = 0; g < groupDNs.size(); g++) {
            String grpDN = (String) groupDNs.get(g);

            // if the group has already been processed, skip it
            if (groupsProcessed.contains(grpDN)) {
                continue;
            }
            else {
                groupsProcessed.add(grpDN);
            }

            // get the group entity and copy the requested properties to return
            // dataobject
            DataObject groupDO = getEntityByUniqueName(grpDN);
            DataObject returnGroupDO = 
                (DataObject) EcoreUtil.create(grpEClass);
            GenericHelper.copyDataObject(returnGroupDO, groupDO, grpProps,
                GenericHelper.IDENTIFIER_REF, mappedPrincipalNameProperty,
                mappedPrincipalNamePropertyMultiValued, repositoryId);
            returnEntity.getList(DO_GROUPS).add(returnGroupDO);

            // if nested group membership is requested, get the groups of this
            // group
            if ((level == PROP_LEVEL_NESTED) || (nextLevel < level)) {
                getGroupMembership(grpDN, returnEntity, grpProps, level,
                    nextLevel, xpathHelper, groupsProcessed);
                // if tree view is to be returned
                //getGroupMembership(grpDN, returnGroupDO, grpEClass, grpProps,
                // level, nextLevel, xpathHelper, groupsProcessed);
            }
        }

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, METHODNAME);
        }
    }

    /**
     * Return the uniqueNames of the matching group member belongs to.
     * 
     * @param mbrDN uniqueName of the member
     * @param xpathHelper XPath helper with parsed search expression
     * @throws WIMException
     */
    private List getGroupsForEntity(String mbrDN, XPathHelper xpathHelper)
        throws WIMException
    {
        final String METHODNAME = "getGroupsForEntity";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME, mbrDN);
        }

        List groups = new ArrayList();
        synchronized (entityDN2DO) {
            // iterate thru all the entities in the repository
            for (Iterator itr = entityDN2DO.keySet().iterator(); 
                itr.hasNext();) {
                String grpDN = (String) itr.next();
                DataObject grpDO = getEntityByUniqueName(grpDN);

                // if the entity is a group, check if it matches the search
                // expression
                if (SchemaHelper.isGroupType(grpDO.getType().getName())) {
                    if (xpathHelper == null || (xpathHelper.evaluate(grpDO))) {
                        // group matches the search expression, check if the
                        // member belongs to this group
                        List members = grpDO.getList(DO_MEMBERS);
                        for (int m = 0; m < members.size(); m++) {
                            DataObject mbrDO = (DataObject) members.get(m);
                            String grpMbrDN = 
                                mbrDO.getString(GenericHelper.UNIQUE_NAME_PATH);

                            // if member matches, add the group to return list
                            if (normalizedStringsAreEqual(mbrDN, grpMbrDN)) {
                                groups.add(grpDN);
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.exiting(CLASSNAME, METHODNAME, "returning:" + groups);
        }
        return groups;
    }

    /**
     * Delete the entity and return entity type and uniqueId of the deleted
     * entity in a list.
     * 
     * @param uniqueName uniqueName of the entity to be deleted.
     * 
     * @return a list containing entity type and uniqueId of the deleted entity.
     * 
     * @throws WIMException
     */
    private synchronized List deleteEntity(String uniqueName)
        throws WIMException
    {
        final String METHODNAME = "deleteEntity(String)";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME, "uniqueName:"
                + uniqueName);
        }

        List returnList = null;
        String entityType = null;

        // remove the entity form the cache
        DataObject entityDO = 
            (DataObject) entityDN2DO.remove(normalizeDN(uniqueName));

        // if the entity was present in the cache, remove the entity from the
        // repository
        if (entityDO != null) {
            // add the entity type and uniqueId to a list: to be used by calling
            // method
            returnList = new ArrayList();
            returnList.add(entityDO.getType().getName());
            String ID = getEntityID(entityDO);
            returnList.add(ID);

            // cleanup other caches
            entityID2DN.remove(ID);

            // delete the entity from repository
            entityDO.delete();

            // decrease the size by 1
            numOfEntities--;
        }
        else {
            throw new EntityNotFoundException(WIMMessageKey.ENTITY_NOT_FOUND,
                WIMMessageHelper.generateMsgParms(uniqueName), CLASSNAME,
                METHODNAME);
        }

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.exiting(CLASSNAME, METHODNAME, "deleted " + returnList
                + ", numOfEntities=" + numOfEntities);
        }
        return returnList;
    }

    /**
     * Remove a member from a group. This method does not save the updated data
     * into the file. Make sure to save the data after calling this method.
     * 
     * @param groupUniqueName uniqueName of the group
     * @param memberUniqueName uniqueName of the member
     * @throws WIMException
     */
    private void removeMemberFromGroup(String groupUniqueName,
        String memberUniqueName) throws WIMException
    {
        final String METHODNAME = "removeMemberFromGroup";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME, "groupUniqueName="
                + groupUniqueName + ", memberUniqueName=" + memberUniqueName);
        }

        // get the group entity dataobject
        DataObject group = getEntityByUniqueName(groupUniqueName);

        boolean deleted = false;

        // iterate thru all the members of the group
        // if the member exists, delete it
        List members = group.getList(DO_MEMBERS);
        for (int i = 0; i < members.size(); i++) {
            DataObject member = (DataObject) members.get(i);

            String mbrDN = member.getString(GenericHelper.UNIQUE_NAME_PATH);
            if (normalizedStringsAreEqual(mbrDN, memberUniqueName)) {
                // member uniqueName matched, delete it and 
                // set the group modify timestamp
                member.delete();
                group.set(PROP_MODIFY_TIMESTAMP, getDateString());
                deleted = true;
                break;
            }
        }

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.exiting(CLASSNAME, METHODNAME, "member deleted="
                + deleted);
        }
    }

    /**
     * Search for entities matching the search expression.
     * 
     * @param entityTypes entity types to match
     * @param xpathHelper XPath helper with parsed search expression
     * @param returnSubType whether to return the subtype of entityType or not
     * 
     * @return List of matching entity dataobjects
     * 
     * @throws Exception
     */
    private List search(List entityTypes, XPathHelper xpathHelper,
        boolean returnSubType) throws Exception
    {
        final String METHODNAME = "search";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME, entityTypes
                + ", searchStr=" + xpathHelper.getNode());
        }

        // initialize the list of search results
        List searchResult = new Vector();

        synchronized (entityDN2DO) {
            // iterate thru the entities
            for (Iterator itr = entityDN2DO.values().iterator(); 
                itr.hasNext();) {
                DataObject entity = (DataObject) itr.next();

                // check if the entity type matches the entity types to be
                // returned
                boolean evaluateThisEntity = false;
                for (int i = 0; i < entityTypes.size(); i++) {
                    String entityType = (String) entityTypes.get(i);
                    if (entityType.equals(entity.getType().getName()) || 
                        (returnSubType && SchemaHelper.isSuperType(
                            entityType, entity.getType().getName()))) {
                        evaluateThisEntity = true;
                        break;
                    }
                }

                // entity type matches
                if (evaluateThisEntity) {
                    // evaluate the expression for this entity
                    if (xpathHelper.evaluate(entity)) {
                        // entity matches the search expression, 
                        // add it to the return list
                        searchResult.add(entity);
                    }
                }
            }
        }

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.exiting(CLASSNAME, METHODNAME,
                "Number of matched entities=" + searchResult.size());
        }
        return searchResult;
    }

    /**
     * Return true if the two passwords match.
     * 
     * @param inPassword input password
     * @param setHashedPassword hashed password set in the repository
     * @throws WIMException
     */
    private boolean checkPassword(byte[] inPassword, byte[] setHashedPassword)
        throws WIMException
    {
        final String METHODNAME = "checkPassword";

        boolean match = false;
        try {
            if ((inPassword != null) && (setHashedPassword != null)) {
                // hash the input password and compare with old hash
                String setHashedPasswordStr = 
                    new String(setHashedPassword).trim();
                byte[] inHashedPassword = hash(inPassword);
                String inHashedPasswordStr = 
                    new String(inHashedPassword).trim();
                if (trcLogger.isLoggable(Level.FINEST)) {
                    trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME,
                        "SHOULD NOT BE TRACED IN PRODUCTION ENVIRONMENT."
                            + " inpHashed=#" + inHashedPasswordStr
                            + "#, setHashedPasswordStr=#"
                            + setHashedPasswordStr + "#");
                }

                if (setHashedPasswordStr.equals(inHashedPasswordStr)) {
                    // password matched
                    match = true;
                }
            }
        }
        catch (Exception e) {
            throw new PasswordCheckFailedException(
                WIMMessageKey.PASSWORD_MATCH_FAILED, null, Level.WARNING,
                CLASSNAME, METHODNAME, e);
        }
        return match;
    }

    /**
     * Delete references of the entity from other entities.
     * 
     * @param uniqueId uniqueId of the entity
     * @param uniqueName uniqueName of the entity
     */
    private synchronized void cleanReferences(String uniqueId,
        String uniqueName) throws Exception
    {
        final String METHODNAME = "cleanReferences";

        boolean logEnabled = trcLogger.isLoggable(Level.FINEST);
        if (logEnabled) {
            trcLogger.entering(CLASSNAME, METHODNAME, "uniqueId=" + uniqueId
                + ", #uniqueName:" + uniqueName);
        }

        // set for deleted entities
        Set references = new HashSet();
        boolean refFound = false;

        // get the current entities defined in file repository
        List entities = entityRoot.getList(DO_ENTITIES);
        if (entities == null || entities.size() == 0) {
            if (logEnabled) {
                trcLogger.exiting(CLASSNAME, METHODNAME,
                    "No entities in the repository");
            }
            return;
        }

        // repeat for each entity
        for (int i = 0; i < entities.size(); i++) {
            DataObject entity = (DataObject) entities.get(i);

            // get the reference properties for this entity type
            Map refProps = (Map) entityReference.get(
                entity.getType().getName());
            if (refProps == null) {
                // this entity type does not have any reference property
                continue;
            }

            // for each reference property, see its value has the
            // uniqueId/uniqueName
            Iterator itr = refProps.keySet().iterator();
            for (; itr.hasNext();) {
                String refProp = (String) itr.next();
                DataObject refDO = hasReference(entity, refProp,
                    (Integer) refProps.get(refProp), uniqueId, uniqueName);

                // if reference is found, delete the reference
                if (refDO != null) {
                    refFound = true;
                    refDO.delete();
                    references.add(entity.getString(
                        GenericHelper.UNIQUE_NAME_PATH));
                    entity.set(PROP_MODIFY_TIMESTAMP, getDateString());
                }
            }
        }

        if (refFound) {
            // at least one reference was found, save the data in file
            // repository
            saveEntities();
        }

        if (logEnabled) {
            trcLogger.exiting(CLASSNAME, METHODNAME,
                "References deleted from:" + references);
        }
    }

    /**
     * Change the references during rename.
     * 
     * @param oldUniqueName old uniqueName to be replaced
     * @param newUniqueName new uniqueName to replace with
     */
    private synchronized void changeReferences(String oldUniqueName,
        String newUniqueName) throws WIMException
    {
        final String METHODNAME = "changeReferences";

        boolean logEnabled = trcLogger.isLoggable(Level.FINEST);
        if (logEnabled) {
            trcLogger.entering(CLASSNAME, METHODNAME, "oldUniqueName:"
                + oldUniqueName + ", #newUniqueName:" + newUniqueName);
        }

        // set for updated entities
        Set references = new HashSet();

        // get the current entities defined in file repository
        List entities = entityRoot.getList(DO_ENTITIES);
        if (entities == null || entities.size() == 0) {
            if (logEnabled) {
                trcLogger.exiting(CLASSNAME, METHODNAME,
                    "No entities in the repository");
            }
            return;
        }

        // repeat for each entity
        for (int i = 0; i < entities.size(); i++) {
            DataObject entity = (DataObject) entities.get(i);
            // get the reference properties for this entity type
            Map refProps = (Map) entityReference.get(
                entity.getType().getName());
            if (refProps == null) {
                // this entity type does not have any reference property
                continue;
            }

            // for each reference property, see its value has the
            // uniqueId/uniqueName
            Iterator itr = refProps.keySet().iterator();
            for (; itr.hasNext();) {
                String refProp = (String) itr.next();
                DataObject refDO = hasReference(entity, refProp, 
                    (Integer) refProps.get(refProp), null, oldUniqueName);
            
                // if reference is found, change its reference
                if (refDO != null) {
                    // reference dataobject is an identifier
                    if (DO_IDENTIFIER.equals(refDO.getType().getName())) {
                        refDO.setString(PROP_UNIQUE_NAME, newUniqueName);
                        refDO.setString(PROP_EXTERNAL_NAME, newUniqueName);
                    }
                    else {
                        // reference dataobject is an entity
                        refDO.setString(GenericHelper.UNIQUE_NAME_PATH,
                            newUniqueName);
                        refDO.setString(GenericHelper.EXTERNAL_NAME_PATH,
                            newUniqueName);
                    }

                    references.add(
                        entity.getString(GenericHelper.UNIQUE_NAME_PATH));
                    entity.set(PROP_MODIFY_TIMESTAMP, getDateString());
                }
            }
        }

        if (logEnabled) {
            trcLogger.exiting(CLASSNAME, METHODNAME,
                "References updated for:" + references);
        }
    }

    /**
     * Return the reference dataobject if its value contains the specified
     * uniqueId or uniqueName.
     * 
     * @param entity entity dataobject, which might contain reference value
     * @param refProp reference property name
     * @param refPropDataObjectType dataobject type of the reference property
     * @param uniqueId uniqueId of the entity whose reference is to be matched
     * @param uniqueName uniqueName of the entity whose reference is to be
     *            matched
     * @return the referenced dataobject
     */
    private DataObject hasReference(DataObject entity, String refProp, 
        Integer refPropDataObjectType, String uniqueId, String uniqueName)
    {
        DataObject refDO = null;

        // reference property is multivalued, check against all values
        List refDOs = entity.getList(refProp);
        if (refDOs != null) {
            // reference property is set, match the identifier of the
            // references
            for (int k = 0; k < refDOs.size(); k++) {
                DataObject ref = (DataObject) refDOs.get(k);

                if (identifierMatches(uniqueId, uniqueName, ref,
                    refPropDataObjectType.intValue())) {
                    // identifier matched
                    refDO = ref;
                    break;
                }
            }
        }

        // return the matching referenced dataobject
        return refDO;
    }

    /**
     * Return true if uniqueIds are not null and they match, or uniqueNames are
     * not null and they match.
     */
    private boolean identifierMatches(String uniqueId, String uniqueName,
        DataObject dataObject, int dataobjectType)
    {
        // dataobject is not set, no match
        if (dataObject == null) {
            return false;
        }

        // if the dataobject is of type entity, get the identifier
        DataObject identifier = (ENTITY_TYPE.intValue() == dataobjectType) ? 
            dataObject.getDataObject(DO_IDENTIFIER) : dataObject;

        // identifier is not set, no match
        if (identifier == null) {
            return false;
        }

        // uniqueId is set, compare if the uniqueId is set for the entity too
        if (uniqueId != null) {
            String uId = identifier.getString(PROP_EXTERNAL_ID);
            if (uId == null) {
                uId = identifier.getString(PROP_UNIQUE_ID);
            }

            if (uId != null && uniqueId.equals(uId)) {
                // uniqueId matches
                return true;
            }
        }

        // compare the uniqueName
        if (uniqueName != null) {
            String uName = identifier.getString(PROP_UNIQUE_NAME);
            if (uName != null && normalizedStringsAreEqual(uniqueName, uName)){
                return true;
            }
        }

        // no match found
        return false;
    }
    
    /**
     * Add a entity type to the schema dataobject.
     */
    private void addEntityToSchemaDO(DataObject schema, String reqEntType)
    {
        String nsURI = SchemaHelper.getTypeNsURI(reqEntType);

        // set the entity type name and namespace URI for the
        // entity type into the return dataobject
        DataObject entitySchemaDO = schema.createDataObject(DO_ENTITY_SCHEMA);
        entitySchemaDO.set(PROP_ENTITY_NAME, reqEntType);
        entitySchemaDO.set(PROP_NS_URI, nsURI);
    }
    
    /**
     * Add a property name to the schema dataobject.
     */
    private void addPropertyToSchemaDO(DataObject schema, String propName)
    {
        DataObject pSchema = schema.createDataObject(DO_PROPERTY_SCHEMA);
        pSchema.setString(PROP_PROPERTY_NAME, propName);
    }
}