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
 * File Name: AbstractAdapterImpl.java
 *
 * Description: Abstract adapter implementation.
 *
 * Change History:
 *
 * mm/dd/yy userid   track 	change history description here
 * -------- ------   ----- -------------------------------------------
 ******************************************************************************/
package com.ibm.ws.wim.adapter.sample;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EReference;

import com.ibm.websphere.wim.ConfigConstants;
import com.ibm.websphere.wim.SchemaConstants;
import com.ibm.websphere.wim.exception.EntityAlreadyExistsException;
import com.ibm.websphere.wim.exception.EntityNotFoundException;
import com.ibm.websphere.wim.exception.InitializationException;
import com.ibm.websphere.wim.exception.OperationNotSupportedException;
import com.ibm.websphere.wim.exception.SearchControlException;
import com.ibm.websphere.wim.exception.WIMApplicationException;
import com.ibm.websphere.wim.exception.WIMException;
import com.ibm.websphere.wim.ras.WIMLogger;
import com.ibm.websphere.wim.ras.WIMMessageHelper;
import com.ibm.websphere.wim.ras.WIMMessageKey;
import com.ibm.websphere.wim.ras.WIMTraceHelper;
import com.ibm.websphere.wim.util.SDOHelper;

import com.ibm.wsspi.wim.ConfigHelper;
import com.ibm.wsspi.wim.GenericHelper;
import com.ibm.wsspi.wim.RepositoryImpl;
import com.ibm.wsspi.wim.SchemaHelper;

import commonj.sdo.ChangeSummary;
import commonj.sdo.DataObject;
import commonj.sdo.Property;

/**
 * This abstract class extends com.ibm.wsspi.wim.RepositoryImpl class which
 * implements virtual member manager Repository SPI. This class does not 
 * contain any repository specific code. It performs basic processing of the
 * SPI methods and defines abstract methods to be implemented by its
 * subclass. For most cases, you may not need to change this file.
 * The implementation of abstract methods will be repository
 * specific. See at the bottom of the file for the abstract methods. 
 * <br>
 * This class can be used as a guide to implement abstract methods to write a
 * custom adapter for virtual member manager. Custom adapter implementation can
 * also extend from com.ibm.wsspi.wim.RepositoryImpl and implement all the SPI
 * methods from scratch.
 * <br>
 * It is not recommended to directly extend this class. Make a copy of
 * this class and change the class name suitable for your adapter. If your
 * adapter only supports users and groups then most probably, you would not
 * need to modify this class. All you need to do is implement the abstract 
 * methods.
 * <p>
 * Most of the methods in this class are public so that it can be overwritten by
 * the subclass.
 * <p>
 * This sample does not support OrgContainer, so the AncestorControl and
 * DescendantControl are not supported. <br>
 * This sample adapter also does not support multi base entries. <br>
 * It also does not support dynamic configuration. To support dynamic
 * configuration update, implement method(s) of
 * {@link com.ibm.websphere.wim.DynamicConfigService} interface.
 * <p>
 * Messages used in this sample code are mostly predefined in virtual member
 * manager. There are some messages which are not defined in virtual member
 * manager and so those messages are not translated.
 * 
 * @author Ranjan Kumar
 */
public abstract class AbstractAdapterImpl extends RepositoryImpl 
        implements SchemaConstants
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
    // P R I V A T E   S T A T I C   C O N S T A N T S
    //---------------------------------------------------------------
    /**
     * The class name for this class (used for logging and tracing).
     */
    private static final String CLASSNAME = 
        AbstractAdapterImpl.class.getName();

    /**
     * The trace logger for this class.
     */
    private static final Logger trcLogger = 
        WIMLogger.getTraceLogger(CLASSNAME);

    //---------------------------------------------------------------
    // P U B L I C    V A R I A B L E S
    //---------------------------------------------------------------
    
    /**
     * The repository id for the adapter.
     */
    public String repositoryId = null;

    /**
     * The configured custom properties for the adapter.
     */
    public Map repositoryConfigProps = new HashMap();

    /**
     * The base entry name for the adapter
     */
    String baseEntryName = null;

    /**
     * The login properties for the repository.
     */
    public List loginProperties = new ArrayList();

    /**
     * Whether the login properties is multi valued or not.
     */
    public List loginPropertiesTypeMultiValued = new ArrayList();

    /**
     * The property name which is mapped to the principalName property of
     * LoginAccount.
     */
    public String mappedPrincipalNameProperty = null;

    /**
     * Whether the mapped principal name property is multi valued or not.
     */
    public boolean mappedPrincipalNamePropertyMultiValued = false;

    //---------------------------------------------------------------
    // C O N S T R U C T O R
    //---------------------------------------------------------------
    /**
     * Default constructor.
     */
    public AbstractAdapterImpl()
    {
    }

    //---------------------------------------------------------------
    // S P I   A N D  O T H E R   S U P P O R T I N G   M E T H O D S
    //---------------------------------------------------------------

    /**
     * Initialize the adapter with the specified repository configuration.
     * 
     * @param reposConfig The DataObject which contains configuration data of
     *            this adapter.
     * 
     * @see com.ibm.wsspi.wim.Repository#initialize(commonj.sdo.DataObject)
     */
    public void initialize(DataObject reposConfig) throws WIMException
    {
        super.initialize(reposConfig);

        final String METHODNAME = "initialize";

        if (trcLogger.isLoggable(Level.FINER)) {
            // log the entry to this method
            // prefix the WIMLogger.SPI_PREFIX to the method name to easily 
            // track method entry and exit for the adapter in the trace file
            trcLogger.entering(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME);
        }

        try {
            // retrieve the repository id from the config
            repositoryId = 
                reposConfig.getString(ConfigConstants.CONFIG_PROP_ID);

            // retrieve and validate the configured custom properties
            retrieveCustomProperties(reposConfig.getList(
                ConfigConstants.CONFIG_DO_CUSTOM_PROPERTIES));

            // retrieve and validate the base entry
            retrieveBaseEntry(
                reposConfig.getList(ConfigConstants.CONFIG_DO_BASE_ENTRIES));

            // retrieve the login properties
            retrieveLoginProperties(reposConfig.getList(
                ConfigConstants.CONFIG_PROP_LOGIN_PROPERTIES));

            if (trcLogger.isLoggable(Level.FINER)) {
                // log the exit from this method
                trcLogger.exiting(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                    "baseEntryName=" + baseEntryName + ", loginProperties=" + 
                    loginProperties + ", mappedPrincipalNameProperty=" +
                    mappedPrincipalNameProperty);
            }
        }
        catch (WIMException we) {
            throw we;
        }
        catch (Exception e) {
            InitializationException ie = new InitializationException(
                WIMMessageKey.REPOSITORY_INITIALIZATION_FAILED,
                WIMMessageHelper.generateMsgParms(repositoryId, 
                    e.getMessage()), Level.SEVERE, CLASSNAME, METHODNAME, e);

            // set the repository id as the source of the exception
            ie.setRootErrorSource(repositoryId);
            throw ie;
        }
    }
    
    /**
     * Creates the entity under the given root data object.
     * 
     * @see com.ibm.wsspi.wim.Repository#create(commonj.sdo.DataObject)
     */
    public DataObject create(DataObject root) throws WIMException
    {
        final String METHODNAME = "create";

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataGraph(root));
        }

        // DataObject that will be returned if the entity is created
        // successfully
        DataObject returnRoot = null;

        // uniqueName of the entity being created
        String uniqueName = null;

        try {
            List entities = root.getList(DO_ENTITIES);
            // for all entities
            for (int i = 0; i < entities.size(); i++) {
                // get the entity and make a copy of it
                DataObject origEntity = (DataObject) entities.get(i);
                DataObject entity = SDOHelper.cloneDataObject(origEntity);
                String entityType = entity.getType().getName();

                DataObject identifier = entity.getDataObject(DO_IDENTIFIER);
                uniqueName = identifier.getString(PROP_UNIQUE_NAME);

                // if entry already exists, throw an exception
                if (entityAlreadyExists(entity)) {
                    throw new EntityAlreadyExistsException(
                        WIMMessageKey.ENTITY_ALREADY_EXIST, 
                        WIMMessageHelper.generateMsgParms(uniqueName), 
                        Level.SEVERE, CLASSNAME, METHODNAME);
                }

                // for LoginAccount, validate that principalName and realm
                // properties are not set
                GenericHelper.checkLoginAccountReadOnlyProperties(entityType,
                    entity, null, repositoryId);

                // validate that all the reference props are valid
                validateReferenceProperties(entity);

                // if the entity contains groups it belongs to,
                // then group entities need to be updated to add this entity as
                // member
                Set groupsToUpdate = null;
                if (entity.isSet(DO_GROUPS)) {
                    List groups = entity.getList(DO_GROUPS);
                    groupsToUpdate = new HashSet();

                    // get the group this entity belongs to and verify that the
                    // group exists
                    for (int k = 0; k < groups.size(); k++) {
                        DataObject groupDO = (DataObject) groups.get(k);
                        String groupUniqueName = getUniqueName(groupDO);
                        if (groupMustExist(null, groupUniqueName)) {
                            groupsToUpdate.add(groupUniqueName);
                        }
                    }
                }

                // if the group entity contains the "members",
                Set membersToUpdate = null;
                if (SchemaHelper.isGroupType(entityType)
                    && entity.isSet(DO_MEMBERS)) {
                    List members = entity.getList(DO_MEMBERS);
                    membersToUpdate = new HashSet();

                    // validate that the member exists
                    for (int k = 0; k < members.size(); k++) {
                        DataObject memberDO = (DataObject) members.get(k);
                        String memberUniqueName = getUniqueName(memberDO);
                        if (memberMustExist(null, memberUniqueName)) {
                            membersToUpdate.add(memberUniqueName);
                        }
                    }
                }

                // create the entity in the repository
                returnRoot = createEntity(entityType, entity);

                // update the group membership
                if (groupsToUpdate != null) {
                    for (Iterator itr = groupsToUpdate.iterator();
                        itr.hasNext();) {
                        String groupUniqueName = (String) itr.next();
                        addMemberToGroup(groupUniqueName, uniqueName);
                    }
                }
                if (membersToUpdate != null) {
                    for (Iterator itr = membersToUpdate.iterator();
                        itr.hasNext();) {
                        String memberUniqueName = (String) itr.next();
                        addMemberToGroup(uniqueName, memberUniqueName);
                    }
                }
            }
        }
        catch (WIMException we) {
            throw we;
        }
        catch (Exception e) {
            WIMException we = new WIMApplicationException(
                WIMMessageKey.ENTITY_CREATE_FAILED, 
                WIMMessageHelper.generateMsgParms(uniqueName, e.getMessage()),
                Level.SEVERE, CLASSNAME, METHODNAME, e);

            // set the repository id as the source of the exception
            we.setRootErrorSource(repositoryId);
            throw we;
        }
        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataGraph(returnRoot));
        }

        return returnRoot;
    }
    
    /**
     * Return information of the specified entities. 
     * <br>
     * Following control dataobject can be passed into a get() call: 
     * <br>
     * PropertyControl, AncestorControl, DescendantControl,
     * GroupMembershipControl, GroupMemberControl, CheckGroupMembershipControl.
     * <br>
     * More than entity and more than one control can be passed in this call.
     * This sample code does not handle AncestorControl and DescendantControl.
     * 
     * @see com.ibm.wsspi.wim.Repository#get(commonj.sdo.DataObject)
     */
    public DataObject get(DataObject root) throws WIMException
    {
        final String METHODNAME = "get";
        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataGraph(root));
        }

        DataObject returnRoot = SDOHelper.createRootDataObject();
        DataObject entity = null;

        try {
            // get the control dataobjects
            Map ctrlMap = GenericHelper.getControlMap(root);
            DataObject propertyCtrl = 
                (DataObject) ctrlMap.get(DO_PROPERTY_CONTROL);
            DataObject grpMbrCtrl = 
                (DataObject) ctrlMap.get(DO_GROUP_MEMBER_CONTROL);
            DataObject grpMbrshipCtrl = 
                (DataObject) ctrlMap.get(DO_GROUP_MEMBERSHIP_CONTROL);
            DataObject checkGrpMbrshipCtrl = 
                (DataObject) ctrlMap.get(DO_CHECK_GROUP_MEMBERSHIP_CONTROL);
            DataObject ancestorCtrl = 
                (DataObject) ctrlMap.get(DO_ANCESTOR_CONTROL);
            DataObject descendantCtrl = 
                (DataObject) ctrlMap.get(DO_DESCENDANT_CONTROL);

            // throw exceptions for unsupported controls
            if (ancestorCtrl != null) {
                throw new WIMApplicationException(
                    "AncestorControl is not supported.");
            }
            if (descendantCtrl != null) {
                throw new WIMApplicationException(
                    "DescendantControl is not supported.");
            }

            List entities = root.getList(DO_ENTITIES);
            // for entities requested in input datagraph, get the information
            // requested
            for (int i = 0; i < entities.size(); i++) {
                entity = (DataObject) entities.get(i);

                // get the properties of the entity
                DataObject returnEntity = getEntity(entity, propertyCtrl,
                    returnRoot);

                // get the group members and properties of the members
                if (grpMbrCtrl != null) {
                    // validate the level
                    GenericHelper.validateNestingLevel(grpMbrCtrl,
                        DO_GROUP_MEMBER_CONTROL);

                    getGroupMembers(returnEntity, grpMbrCtrl);
                }

                // get the groups the entity belongs to.
                // also get the properties of the group
                if (grpMbrshipCtrl != null) {
                    // validate the level
                    GenericHelper.validateNestingLevel(grpMbrshipCtrl,
                        DO_GROUP_MEMBERSHIP_CONTROL);

                    getGroupMembership(returnEntity, grpMbrshipCtrl);
                }

                // check the group membership
                if (checkGrpMbrshipCtrl != null) {
                    // validate the level
                    GenericHelper.validateNestingLevel(checkGrpMbrshipCtrl,
                        DO_CHECK_GROUP_MEMBERSHIP_CONTROL);

                    checkGroupMembership(entity, returnRoot,
                        checkGrpMbrshipCtrl);
                }
            }
        }
        catch (WIMException we) {
            throw we;
        }
        catch (Exception e) {
            WIMException we = new WIMApplicationException(
                WIMMessageKey.ENTITY_GET_FAILED, 
                WIMMessageHelper.generateMsgParms(
                    GenericHelper.getIdentifierString(entity), e.getMessage()), 
                    Level.SEVERE, CLASSNAME, METHODNAME, e);
            we.setRootErrorSource(repositoryId);
            throw we;
        }

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataGraph(returnRoot));
        }

        return returnRoot;
    }
    
    /**
     * Delete the entity specified in the root dataobject.
     * 
     * @see com.ibm.wsspi.wim.Repository#delete(commonj.sdo.DataObject)
     */
    public DataObject delete(DataObject root) throws WIMException
    {
        final String METHODNAME = "delete";
        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataGraph(root));
        }

        DataObject returnRoot = deleteEntities(root);

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataGraph(returnRoot));
        }
        return returnRoot;
    }
    
    /**
     * Updates entity specified in the root dataobject. Input dataobject could
     * contain a ChangeSummary that can be used to access the change history for
     * any dataobject in the datagraph.
     * <br> 
     * Input dataobject could also contain GroupMembershipControl or 
     * GroupMemberControl, which can be used to change the group membership:
     * <OL>
     * <LI>To add an entity to a Group, the caller can add the entity 
     * dataobject with "groups" property and GroupMembershipControl 
     * to the root dataobject. 
     * <LI>To remove an entity from a Group, the "modifyMode" property of the 
     * GroupMembershipControl will be set to "3".
     * </OL>
     * 
     * @see com.ibm.wsspi.wim.Repository#update(commonj.sdo.DataObject)
     */
    public DataObject update(DataObject root) throws WIMException
    {
        final String METHODNAME = "update";

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataGraph(root));
        }

        DataObject returnRoot = null;

        // check if ChangeSummary is used to update the entity.
        // To utilize the ChangeSummary, the caller first calls the "get" API
        // to retrieve the dataobject with properties that needs to be updated
        ChangeSummary changeSummary = root.getDataGraph().getChangeSummary();
        List changes = changeSummary.getChangedDataObjects();
        if (changes.size() > 0) {
            // entity is updated with change summary
            returnRoot = updateWithChangeSummary(root);
        }
        else {
            // change summary is not used, use the modification mode in control
            // to detect what has changed
            returnRoot = updateWithoutChangeSummary(root);
        }

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataGraph(returnRoot));
        }
        return returnRoot;
    }
    
    /**
     * Search the profile repositories for entities matching the given search
     * expression and returns them with the requested properties.
     * 
     * @see com.ibm.wsspi.wim.Repository#search(commonj.sdo.DataObject)
     */
    public DataObject search(DataObject root) throws WIMException
    {
        final String METHODNAME = "search";
        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataGraph(root));
        }

        // get the SearchControl. PageControl and SortControl should be ignored
        Map ctrlMap = GenericHelper.getControlMap(root);
        DataObject searchControl = 
            (DataObject) ctrlMap.get(DO_SEARCH_CONTROL);
        String searchExpr = searchControl.getString(PROP_SEARCH_EXPRESSION);

        // check that the search expression is set
        if (searchExpr == null || searchExpr.length() == 0) {
            throw new SearchControlException(
                WIMMessageKey.MISSING_SEARCH_EXPRESSION, Level.SEVERE,
                CLASSNAME, METHODNAME);
        }

        // search the entities in the repository
        DataObject returnRoot = searchEntities(searchControl);

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataGraph(returnRoot));
        }
        return returnRoot;
    }
    
    /**
     * Authenticate the account data object in the specified root data object.
     * 
     * @see com.ibm.wsspi.wim.Repository#login(commonj.sdo.DataObject)
     */
    public DataObject login(DataObject root) throws WIMException
    {
        final String METHODNAME = "login";
        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataGraph(root));
        }

        // get the LoginControl
        Map ctrlMap = GenericHelper.getControlMap(root);
        DataObject loginCtrl = (DataObject) ctrlMap.get(DO_LOGIN_CONTROL);

        DataObject account = root.getDataObject(GenericHelper.DO_FIRST_ENTITY);

        // authenticate the user
        DataObject returnRoot = login(account, loginCtrl);

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataGraph(returnRoot));
        }

        return returnRoot;
    }

    /**
     * Creates the schema of new entity types and property types at runtime.
     * Abstract adapter does not support this feature. Overwrite this method to
     * support this feature.
     * 
     * @see com.ibm.wsspi.wim.Repository#createSchema(commonj.sdo.DataObject)
     */
    public DataObject createSchema(DataObject root) throws WIMException
    {
        final String METHODNAME = "createSchema";
        throw new OperationNotSupportedException(
            WIMMessageKey.OPERATION_NOT_SUPPORTED_IN_REPOSITORY,
            WIMMessageHelper.generateMsgParms(METHODNAME, repositoryId),
            CLASSNAME, METHODNAME);
    }

    /**
     * Retrieve the repository specific schema information of entity types and
     * property types.
     * 
     * @see com.ibm.wsspi.wim.Repository#getSchema(commonj.sdo.DataObject) File
     *      Registry schema is same as WIM default schema
     */
    public DataObject getSchema(DataObject inRoot) throws WIMException
    {
        final String METHODNAME = "getSchema";
        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataObject(inRoot));
        }

        // get the control dataobjects
        Map ctrlMap = GenericHelper.getControlMap(inRoot);
        DataObject dataTypeCtrl =
            (DataObject) ctrlMap.get(DO_DATATYPE_CONTROL);
        DataObject propDefCtrl = 
            (DataObject) ctrlMap.get(DO_PROPERTY_DEFINITION_CONTROL);
        DataObject entityTypeCtrl = 
            (DataObject) ctrlMap.get(DO_ENTITY_TYPE_CONTROL);

        // retrieve the repository schema
        DataObject returnRoot = getSchema(dataTypeCtrl, propDefCtrl,
            entityTypeCtrl);

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, WIMLogger.SPI_PREFIX + METHODNAME,
                WIMTraceHelper.printDataObject(returnRoot));
        }

        return returnRoot;
    }
    
    
    //---------------------------------------------------------------
    // S U P P O R T I N G    M E T H O D S
    //---------------------------------------------------------------


    /**
     * Retrieve and validate the configured custom properties.
     * 
     * @param customProps List of configured custom properties
     *
     * @throws InitializationException if configuration error is encountered
     */
    public void retrieveCustomProperties(List customProps)
        throws InitializationException
    {
        final String METHODNAME = "initializeCustomProperties";

        Iterator iter = customProps.iterator();
        // for each configured custom properties
        while (iter.hasNext()) {
            // get the next property & extract the property name and value
            DataObject prop = (DataObject) iter.next();
            String propName = 
                prop.getString(ConfigConstants.CONFIG_PROP_NAME);
            String propValue = 
                prop.getString(ConfigConstants.CONFIG_PROP_VALUE);

            if (trcLogger.isLoggable(Level.FINEST)) {
                trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME,
                    "Custom Property: name=[ " + propName + " ], value=[ "
                        + propValue + " ]");
            }

            // get the key by converting to lower-case (case-insensitive)
            String key = propName.toLowerCase();

            // check if the key is a valid property and value is valid too
            if (!isValidCustomProperty(key, propValue)) {
                throw new InitializationException(
                    "Unrecognized custom configuration property value. "
                        + "property=[" + propName + "], value=[" + propValue
                        + "]", Level.SEVERE, CLASSNAME, METHODNAME);
            }

            // add the property to the map
            // if a value already exists it will be overwritten
            repositoryConfigProps.put(key, propValue);
        }
        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME,
                "repositoryConfigProps=" + repositoryConfigProps);
        }
    }

    /**
     * Retrieve and validate the base entry.
     * 
     * @param baseEnrties list of configured base entries
     * 
     * @throws InitializationException if configuration error is encountered
     */
    public void retrieveBaseEntry(List baseEntries)
        throws InitializationException
    {
        final String METHODNAME = "retrieveBaseEntry";

        if (baseEntries.size() != 1) {
            throw new InitializationException(
                "Zero or more than one base entry configured.", Level.SEVERE,
                CLASSNAME, METHODNAME);
        }

        // get the base entry
        DataObject baseEntry = (DataObject) baseEntries.get(0);
        // set the base entry name
        baseEntryName = 
            baseEntry.getString(ConfigConstants.CONFIG_PROP_NAME).trim();
    }

    /**
     * Retrieve the login properties for LoginAccount and set the first login
     * property as the property used for principalName.
     * 
     * @param configLoginProps list of configured login properties
     */
    public void retrieveLoginProperties(List configLoginProps)
    {
        List loginProps = null;
        if (configLoginProps.size() > 0) {
            // use the configured loginProperties
            loginProps = configLoginProps;
        }
        else {
            // RDN prop of PersonAccount will be the login prop
            loginProps = (List) ConfigHelper.getEntityRDNs().get(
                DO_PERSON_ACCOUNT);
        }
        
        // process the login properties
        for (int i = 0; i < loginProps.size(); i++) {
            String prop = (String) loginProps.get(i);
            loginProperties.add(prop);
            boolean multiValued = 
                SchemaHelper.isMultiValuedProperty(DO_PERSON_ACCOUNT, prop);
            loginPropertiesTypeMultiValued.add(Boolean.valueOf(multiValued));
        }

        // if there are multiple login properties, first one will be treated as
        // the principalName
        if (loginProperties.size() > 0) {
            mappedPrincipalNameProperty = (String) loginProperties.get(0);
            mappedPrincipalNamePropertyMultiValued = 
              ((Boolean)loginPropertiesTypeMultiValued.get(0)).booleanValue();
        }
    }

    /**
     * Update the entity using the change summary.
     * 
     * @throws WIMException
     */
    public DataObject updateWithChangeSummary(DataObject root)
        throws WIMException
    {
        final String METHODNAME = "updateWithChangeSummary";

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, METHODNAME);
        }

        DataObject returnRoot = SDOHelper.createRootDataObject();

        String uniqueName = null;

        try {
            // get the change summary
            ChangeSummary changeSummary = 
                root.getDataGraph().getChangeSummary();
            List changes = changeSummary.getChangedDataObjects();

            // iterate thru the changed entities
            for (Iterator iter = changes.iterator(); iter.hasNext();) {
                DataObject changedObject = (DataObject) iter.next();
                String type = changedObject.getType().getName();

                Property element = changedObject.getContainmentProperty();
                String elementName = element.getName();

                // Do not allow changes to "Entity" type internal
                // objects(parent, group, etc.)
                if (!DO_ENTITIES.equals(elementName)) {
                    continue;
                }

                // get the uniqueName of the changed entity
                uniqueName = 
                    changedObject.getString(GenericHelper.UNIQUE_NAME_PATH);
                if (trcLogger.isLoggable(Level.FINEST)) {
                    trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME,
                        "updating: " + uniqueName);
                }

                List modifyItems = new ArrayList();
                String newUniqueName = null;

                // iterate thru the changed values and determine if the property
                // has been added, removed, or replaced
                for (Iterator settingIter = changeSummary.getOldValues(
                    changedObject).iterator(); settingIter.hasNext();) {
                    ChangeSummary.Setting changeSetting = 
                        (ChangeSummary.Setting) settingIter.next();

                    Property changedProperty = changeSetting.getProperty();
                    String propName = changedProperty.getName();

                    // for LoginAccount, validate that the principalName
                    // and realm are not set
                    GenericHelper.checkLoginAccountReadOnlyProperties(type,
                        null, propName, repositoryId);

                    // get the old and new value(s)
                    Object oldValue = changeSetting.getValue();
                    Object newValue = changedObject.get(changedProperty);

                    // if the RDN property has changed
                    if (ConfigHelper.isRDNProperty(type, propName)) {
                        // uniqueName has changed, get the new uniqueName
                        // uniqueId stays the same
                        newUniqueName = GenericHelper.getNewUniqueName(type,
                            changedObject, uniqueName);
                    }

                    // for multivalued properties
                    if ((oldValue instanceof List)
                        && (((List) oldValue).size() == 0)) {
                        oldValue = null;
                    }
                    if ((newValue instanceof List)
                        && (((List) newValue).size() == 0)) {
                        newValue = null;
                    }

                    if (oldValue == null) {
                        // old value was not set => new value is added
                        modifyItems.add(new ModificationItem(
                            DirContext.ADD_ATTRIBUTE, new BasicAttribute(
                                propName, newValue)));
                    }
                    else if (newValue == null) {
                        // old value was set but new value is not set => old
                        // value is removed
                        modifyItems.add(new ModificationItem(
                            DirContext.REMOVE_ATTRIBUTE, new BasicAttribute(
                                propName, newValue)));
                    }
                    else {
                        // old value is being replaced with new values
                        modifyItems.add(new ModificationItem(
                            DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(
                                propName, newValue)));
                    }

                    if (trcLogger.isLoggable(Level.FINEST)) {
                        trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME,
                            "changed " + changedProperty.getName()
                                + " from '" + oldValue + "' to '" + newValue
                                + "'");
                    }
                }

                // if the entity is renamed, first rename it then modify its
                // property
                if (newUniqueName != null) {
                    rename(type, changedObject, uniqueName, newUniqueName);
                    uniqueName = newUniqueName;
                }

                // update the entity properties
                updateEntity(changedObject, uniqueName, modifyItems);
                getEntity(changedObject, null, returnRoot);
            }
            if (trcLogger.isLoggable(Level.FINEST)) {
                trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME,
                    "entity updated: " + uniqueName);
            }
        }
        catch (WIMException we) {
            throw we;
        }
        catch (Exception e) {
            WIMException we = new WIMApplicationException(
                WIMMessageKey.ENTITY_UPDATE_FAILED, 
                WIMMessageHelper.generateMsgParms(uniqueName, e.getMessage()),
                Level.SEVERE, CLASSNAME, METHODNAME, e);
            we.setRootErrorSource(repositoryId);
            throw we;
        }

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, METHODNAME);
        }

        return returnRoot;
    }

    /**
     * Update the entity (without change summary). Properties values will be
     * replaced with the new values.
     * 
     * @throws WIMException
     */
    public DataObject updateWithoutChangeSummary(DataObject root)
        throws WIMException
    {
        final String METHODNAME = "updateWithoutChangeSummary";

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.entering(CLASSNAME, METHODNAME);
        }

        DataObject returnRoot = SDOHelper.createRootDataObject();
        String uniqueName = null;
        try {
            // get the Control dataobjects
            Map ctrlMap = GenericHelper.getControlMap(root);

            // get the entities and update them
            List entities = root.getList(DO_ENTITIES);
            for (int i = 0; i < entities.size(); i++) {
                DataObject entity = (DataObject) entities.get(i);
                uniqueName = getUniqueName(entity);
                String type = entity.getType().getName();

                // for LoginAccount, validate that the principalName
                // and realm are not set
                GenericHelper.checkLoginAccountReadOnlyProperties(type, entity,
                    null, repositoryId);

                // list containing the propeties to be replaced
                List modItemList = new ArrayList();
                // list containing the groups to be updated
                List groupMembershipsToUpdate = null;
                // list containing hte group members to be udpated
                List groupMembersToUpdate = null;
                String newUniqueName = null;

                // iterate thru all the properties of this entity type and
                // determine which properties have changed
                EList features = SchemaHelper.getEClass(
                    entity.getType()).getEAllStructuralFeatures();
                for (int j = 0; j < features.size(); j++) {
                    // build the list of properties to be replaced
                    if (features.get(j) instanceof EAttribute) {
                        EAttribute eAttr = (EAttribute) features.get(j);
                        String attrName = eAttr.getName();

                        // ignore the properties that are not set or the
                        // identifier properties
                        if ((!entity.isSet(attrName))
                            || (PROP_UNIQUE_NAME.equals(attrName))
                            || (PROP_UNIQUE_ID.equals(attrName))
                            || (PROP_EXTERNAL_NAME.equals(attrName))
                            || (PROP_EXTERNAL_ID.equals(attrName))) {
                            continue;
                        }

                        // if RDN property is changed, validate that it has a
                        // valid value
                        if (ConfigHelper.isRDNProperty(type, attrName)) {
                            newUniqueName = GenericHelper.getNewUniqueName(
                                type, entity, uniqueName);
                        }

                        // add the properties to the modification item list
                        // these properties values will be replaced
                        if (eAttr.getUpperBound() == 1) {
                            // singlevalued property
                            Attribute attr = new BasicAttribute(attrName,
                                entity.get(attrName));
                            modItemList.add(new ModificationItem(
                                DirContext.REPLACE_ATTRIBUTE, attr));
                        }
                        else {
                            // there is no upper bound: multivalued property
                            Attribute attr = new BasicAttribute(attrName,
                                entity.getList(attrName));
                            modItemList.add(new ModificationItem(
                                DirContext.REPLACE_ATTRIBUTE, attr));
                        }
                    } // end if non-reference property
                    else if (features.get(j) instanceof EReference) {
                        // build the list of reference properties
                        EReference eRef = (EReference) features.get(j);
                        String refName = eRef.getName();

                        // ignore the references that are not set or identifier
                        // and children reference properties
                        if ((!entity.isSet(refName)) 
                            || (DO_IDENTIFIER.equals(refName))
                            || (DO_CHILDREN.equals(refName))) {
                            continue;
                        }

                        // check if members of the group have changed
                        if (DO_MEMBERS.equals(refName)) {
                            groupMembersToUpdate = getGroupMembersToUpdate(
                                entity.getList(refName));
                        }

                        // check if groups of an entity has changed
                        else if (DO_GROUPS.equals(refName)) {
                            groupMembershipsToUpdate = getGroupMembershipsToUpdate(
                                entity.getList(refName));
                        }
                        else {
                            // replace the other reference properties: manager,
                            // secretary, etc.
                            if (eRef.getUpperBound() == 1) {
                                // singlevalued reference property
                                Attribute attr = new BasicAttribute(refName,
                                    entity.get(refName));
                                modItemList.add(new ModificationItem(
                                    DirContext.REPLACE_ATTRIBUTE, attr));
                            }
                            else {
                                // there is no upper bound: multivalued
                                // reference property
                                Attribute attr = new BasicAttribute(refName,
                                    entity.getList(refName));
                                modItemList.add(new ModificationItem(
                                    DirContext.REPLACE_ATTRIBUTE, attr));
                            }

                        }
                    } // end - if reference property
                } // end - for each property

                // if the entity is renamed, first rename it then modify its
                // property
                if (newUniqueName != null) {
                    rename(type, entity, uniqueName, newUniqueName);
                    uniqueName = newUniqueName;
                }

                if (modItemList.size() > 0) {
                    updateEntity(entity, uniqueName, modItemList);
                }

                // update the group members
                if (groupMembersToUpdate != null) {
                    // get the group member modify mode. default is add.
                    DataObject grpMbrCtrl = 
                        (DataObject) ctrlMap.get(DO_GROUP_MEMBER_CONTROL);
                    int grpMbrMod = DirContext.ADD_ATTRIBUTE;
                    if (grpMbrCtrl != null) {
                        grpMbrMod = grpMbrCtrl.getInt(PROP_MODIFY_MODE);
                    }
                    updateGroupMembers(entity, uniqueName,
                        groupMembersToUpdate, grpMbrMod);
                }

                // update the group membership
                if (groupMembershipsToUpdate != null) {
                    // get the group membership modify mode. default is add.
                    DataObject grpMbrshipCtrl = 
                        (DataObject) ctrlMap.get(DO_GROUP_MEMBERSHIP_CONTROL);
                    int grpMbrshipMod = DirContext.ADD_ATTRIBUTE;
                    if (grpMbrshipCtrl != null) {
                        grpMbrshipMod=grpMbrshipCtrl.getInt(PROP_MODIFY_MODE);
                    }
                    updateGroupMembership(entity, uniqueName,
                        groupMembershipsToUpdate, grpMbrshipMod);
                }

                // add the updated entity to the returnRoot dataobject
                getEntity(entity, null, returnRoot);
            } // for each entity
        }
        catch (WIMException we) {
            throw we;
        }
        catch (Exception e) {
            WIMException we = new WIMApplicationException(
                WIMMessageKey.ENTITY_UPDATE_FAILED, 
                WIMMessageHelper.generateMsgParms(uniqueName, e.getMessage()),
                Level.SEVERE, CLASSNAME, METHODNAME, e);
            we.setRootErrorSource(repositoryId);
            throw we;
        }

        if (trcLogger.isLoggable(Level.FINER)) {
            trcLogger.exiting(CLASSNAME, METHODNAME);
        }

        return returnRoot;
    }

    /**
     * Get the members from the dataobject.
     */
    public Vector getGroupMembersToUpdate(List members)
        throws EntityNotFoundException
    {
        Vector groupMembersToUpdate = null;
        if (members.size() > 0) {
            groupMembersToUpdate = new Vector();
            for (int k = 0; k < members.size(); k++) {
                // get the member uniqueName and validate the members
                String memberUniqueName = getUniqueName(
                    (DataObject) members.get(k));
                if (entityMustExist(null, memberUniqueName)) {
                    groupMembersToUpdate.add(memberUniqueName);
                }
            }
        }
        return groupMembersToUpdate;
    }
    
    /**
     * Get the groups from the dataobject.
     */
    public Vector getGroupMembershipsToUpdate(List groups)
        throws WIMException
    {
        Vector groupMembershipsToUpdate = null;
        if (groups.size() > 0) {
            groupMembershipsToUpdate = new Vector();
            for (int k = 0; k < groups.size(); k++) {
                // get the group uniqueName and validate that the group exists
                String groupUniqueName = getUniqueName(
                    (DataObject) groups.get(k));
                if (groupMustExist(null, groupUniqueName)) {
                    groupMembershipsToUpdate.add(groupUniqueName);
                }
            }
        }
        return groupMembershipsToUpdate;
    }
    
    
    //---------------------------------------------------------------
    //   A B S T R A C T    M E T H O D S
    //
    // A subclass should implement these repository specific methods.
    //---------------------------------------------------------------

    /**
     * Validate the custom property key and value.
     * 
     * @param key The name of the custom property.
     * @param value The value of the custom property.
     * 
     * @return true if the property key anf value is valid.
     */
    public abstract boolean isValidCustomProperty(String key, String value);

    /**
     * Return true if the entity exists in the repository.
     * 
     * @param entity entity dataobject.
     */
    public abstract boolean entityAlreadyExists(DataObject entity);

    /**
     * Return true if the entity with specified uniqueId or uniqueName exists
     * in the repository. If the uniqueName is same as the baseEntry name then
     * it should return true. (baseEntry name is set as the 'parent' of the 
     * entity). 
     * 
     * @param uniqueId uniqueId of the entity.
     * @param uniqueName uniqueName of the entity.
     * 
     * @see #entityMustExist(String, String)
     */
    public abstract boolean entityAlreadyExists(String uniqueId,
        String uniqueName);

    /**
     * Return true if the entity with specified uniqueId or uniqueName exists
     * in the repository.
     * 
     * @param uniqueId uniqueId of the entity.
     * @param uniqueName uniqueName of the entity.
     * 
     * @throws EntityNotFoundException if the entity does not exist.
     * 
     * @see #entityMustExist(String, String)
     */
    public abstract boolean entityMustExist(String uniqueId, String uniqueName)
        throws EntityNotFoundException;

    /**
     * Check if the group entity with specified uniqueId or uniqueName exists
     * in the repository.
     * 
     * @param uniqueId uniqueId of the entity.
     * @param uniqueName uniqueName of the entity.
     * 
     * @throws WIMException if the entity does not exist or if the entity is not
     *             a group.
     */
    public abstract boolean groupMustExist(String uniqueId, String uniqueName)
        throws WIMException;

    /**
     * Check if the member entity with specified uniqueId or uniqueName exists
     * in the repository.
     * 
     * @param uniqueId uniqueId of the entity.
     * @param uniqueName uniqueName of the entity.
     * 
     * @throws WIMException if the entity does not exist.
     */
    public abstract boolean memberMustExist(String uniqueId, String uniqueName)
        throws WIMException;

    /**
     * Validate that all the reference properties are valid: parent, children,
     * members, group, manager, etc.
     * 
     * @param entity entity being created
     * 
     * @throws WIMException
     */
    public abstract void validateReferenceProperties(DataObject entity)
        throws WIMException;

    /**
     * Get the uniqueName of an entity. If the entity's identifier contains
     * uniqueId then uniqueId has precedence over uniqueName.
     * 
     * @param entity Entity dataobject with identifier.
     * 
     * @return The uniqueName of the entity.
     * 
     * @throws EntityNotFoundException if the entity is nt found.
     */
    public abstract String getUniqueName(DataObject entity)
        throws EntityNotFoundException;

    /**
     * Create the entity in the repository.
     * 
     * @param entityType Entity type.
     * @param entity Entity dataobject.
     * 
     * @return The return dataobject containing the entity identifier.
     * 
     * @throws WIMException If creation of the entity fails.
     */
    public abstract DataObject createEntity(String entityType,
        DataObject entity) throws WIMException;

    /**
     * Add a member to a group.
     * 
     * @param groupUniqueName uniqueName of the group entity.
     * @param memberUniqueName uniqueName of the member entity.
     * 
     * @throws WIMException If group member assignement fais.
     */
    public abstract void addMemberToGroup(String groupUniqueName,
        String memberUniqueName) throws WIMException;

    /**
     * Return the entity with the specified property.
     * 
     * @param entity Entity dataobject with identifier.
     * @param propertyCtrl PropertyControl dataobject containing properties to
     *            be returned.
     * @param returnRoot Return root dataobject to which the entity should be
     *            added.
     * 
     * @return the retrieved entity with specified property.
     * 
     * @throws WIMException
     */
    public abstract DataObject getEntity(DataObject entity,
        DataObject propertyCtrl, DataObject returnRoot) throws WIMException;

    /**
     * Retrieve the members of a group entity. Members returned should match the
     * search expression specified in the GroupMemberControl. Members should
     * contain the applicable properties specified in the GroupMemberControl.
     * Add the members to the input group entity.
     * 
     * @param grpEntity Group entity dataobject.
     * @param grpMemberCtrl GroupMemberControl dataobject.
     * @throws WIMException
     */
    public abstract void getGroupMembers(DataObject grpEntity,
        DataObject grpMemberCtrl) throws WIMException;

    /**
     * Retrieve the groups of a member entity. Groups returned should match the
     * search expression specified in the GroupMembershipControl. Groups should
     * contain the applicable properties specified in the
     * GroupMembershipControl. Add the groups to the input member entity.
     * 
     * @param mbrEntity Member entity dataobject.
     * @param grpMembershipCtrl GroupMembershipControl dataobject.
     * 
     * @throws WIMException
     */
    public abstract void getGroupMembership(DataObject mbrEntity,
        DataObject grpMembershipCtrl) throws WIMException;

    /**
     * Check if the specified member belongs to specified group or if the
     * specified group contains the specified member. Set the
     * SchemaConstants.PROP_IN_GROUP property of the CheckGroupMembershipControl
     * in the returnRoot dataobject.
     * 
     * @param entity Group or member entity dataobject containing member or
     *            group.
     * @param returnRoot Return root dataobject.
     * @param checkGrpMbrshipCtrl CheckGroupMembershipControl dataobject.
     * 
     * @throws WIMException
     */
    public abstract void checkGroupMembership(DataObject entity,
        DataObject returnRoot, DataObject checkGrpMbrshipCtrl)
        throws WIMException;

    /**
     * Delete the entity from the repository. Also maintain the referential
     * integrity.
     * 
     * @param root Root dataobject containing the entity to be deleted with or
     *            without DeleteControl.
     * 
     * @return the Root dataobject containing the entities (with only the
     *         identifiers) deleted.
     * 
     * @throws WIMException
     */

    public abstract DataObject deleteEntities(DataObject root)
        throws WIMException;

    /**
     * Update the entity properties in the repository.
     * 
     * @param entity Entity dataobject to be updated. If null, use the
     *            uniqueName to to find the entity to be updated.
     * @param uniqueName uniqueName of the entity.
     * @param modItemList List of ModificationItems.
     * @param returnRoot the root dataobject to which the entity dataobject with
     *            identifier should be returned.
     * 
     * @throws WIMException
     */
    public abstract void updateEntity(DataObject entity, String uniqueName,
        List modItemList) throws WIMException;

    /**
     * Search the entities in the repository.
     * 
     * @param searchControl SearchControl dataobject.
     * @return The root dataobject containing the matching entities with the
     *         properties requested.
     * @throws WIMException
     */
    public abstract DataObject searchEntities(DataObject searchControl)
        throws WIMException;

    /**
     * Authenticate the user in the repository.
     * 
     * @param account Account entity dataobject.
     * @param loginCtrl LoginControl dataobject.
     * 
     * @return The authenticated dataobject.
     * 
     * @throws WIMException if authentication fails, or duplicate entry is
     *             found.
     */
    public abstract DataObject login(DataObject account, DataObject loginCtrl)
        throws WIMException;

    /**
     * Retrieve the repository schema.
     * 
     * @param dataTypeCtrl DataTypeControl dataobject.
     * @param propDefCtrl PropertyDefinitionControl dataobject.
     * @param entityTypeCtrl EntityTypeControl dataobject.
     * 
     * @return The output root dataobject which contains the requested schemas.
     * 
     * @throws WIMException
     */
    public abstract DataObject getSchema(DataObject dataTypeCtrl,
        DataObject propDefCtrl, DataObject entityTypeCtrl)
        throws WIMException;

    /**
     * Rename an entity in the repository.
     * 
     * @param entityType entity type
     * @param updatedEntity updated entity dataobject
     * @param uniqueName current uniqueName of the entity
     * @param newUniqueName new uniqueName of the entity
     * 
     * @throws WIMException
     */
    public abstract void rename(String entityType, DataObject updatedEntity,
        String uniqueName, String newUniqueName) throws WIMException;

    /**
     * Update the group members.
     * 
     * @param entity group dataobject for which the member needs to be updated
     * @param groupUniqueName group's uniqueName for which the member needs to
     *            be updated
     * @param memberUniqueNames uniqueNames of the member entities
     * @param grpMbrMod modification mode:
     *            SchemaConstants.VALUE_MODIFY_MODE_REPLACE,
     *            SchemaConstants.VALUE_MODIFY_MODE_ASSIGN, or
     *            SchemaConstants.VALUE_MODIFY_MODE_UNASSIGN
     * 
     * @throws WIMException
     */
    public abstract void updateGroupMembers(DataObject entity,
        String groupUniqueName, List memberUniqueNames, int grpMbrMod)
        throws WIMException;

    /**
     * Update group membership of an entity.
     * 
     * @param entity member dataobject for which the group assignment needs to
     *            be updated.
     * @param memberUniqueName uniqueName of the member for which the group
     *            assignment needs to be updated.
     * @param groupUniqueNames uniqueNames of the groups
     * @param grpMbrshipMod modification mode:
     *            SchemaConstants.VALUE_MODIFY_MODE_REPLACE,
     *            SchemaConstants.VALUE_MODIFY_MODE_ASSIGN, or
     *            SchemaConstants.VALUE_MODIFY_MODE_UNASSIGN
     * 
     * @throws WIMException
     */
    public abstract void updateGroupMembership(DataObject entity,
        String memberUniqueName, List groupUniqueNames, int grpMbrshipMod)
        throws WIMException;

}
