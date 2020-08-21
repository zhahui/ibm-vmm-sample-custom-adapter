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
 * File Name: XPathHelper.java
 *
 * Description: Parses and evaluates XPath sesarch expression.
 *
 * Change History:
 *
 * mm/dd/yy userid   track 	change history description here
 * -------- ------   ----- -------------------------------------------
 ************************************************************************/
package com.ibm.ws.wim.adapter.sample;

import java.io.StringReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.websphere.wim.SchemaConstants;
import com.ibm.websphere.wim.exception.InvalidArgumentException;
import com.ibm.websphere.wim.exception.SearchControlException;
import com.ibm.websphere.wim.exception.WIMApplicationException;
import com.ibm.websphere.wim.exception.WIMException;
import com.ibm.websphere.wim.ras.WIMLogger;
import com.ibm.websphere.wim.ras.WIMMessageKey;
import com.ibm.websphere.wim.ras.WIMMessageHelper;

import com.ibm.ws.wim.xpath.TokenMgrError;
import com.ibm.ws.wim.xpath.WIMXPathInterpreter;
import com.ibm.ws.wim.xpath.mapping.datatype.LogicalNode;
import com.ibm.ws.wim.xpath.mapping.datatype.ParenthesisNode;
import com.ibm.ws.wim.xpath.mapping.datatype.PropertyNode;
import com.ibm.ws.wim.xpath.mapping.datatype.XPathNode;
import com.ibm.wsspi.wim.GenericHelper;
import com.ibm.wsspi.wim.SchemaHelper;

import commonj.sdo.DataObject;

/**
 * This class parses and evaluates a search expression. Search expressions are
 * specified in XPath format and is set in searchExpression property of
 * SearchControl. There are other helper classes
 * {@link com.ibm.ws.wim.xpath.db.util.DBXPathTranslateHelper}
 * {@link com.ibm.ws.wim.xpath.ldap.util.LdapXPathTranslateHelper} for
 * transforming a search expression to SQL and LDAP queries. 
 * <br>
 * This class does not transform the search expression in any form. It parses
 * the search expression and builds an expression tree. For evaluation, it
 * traverses the tree node by node, matches the property-value, and evaluates
 * the logical operators. 
 * <br>
 * If searchBase is set in the SearchControl then it is compared against the
 * uniqueName of the entity. If the entity's uniqueName ends with the searchBase
 * then the search expression is evaluated for the entity. 
 * <br>
 * The evaluateXPathNode(PropertyNode node) evaluates the search expression
 * using the entity dataobject. This method can be overwritten or updated to
 * perform evaluation against a property Map or some other object. 
 * <br>
 * This class performs the evaluation on normalized values(case insensitive
 * comparisons). Modify the string normalization methods to perform case
 * sensitive comparisons.
 * 
 * @author Ranjan Kumar
 */
public class XPathHelper
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
    private static final String CLASSNAME = XPathHelper.class.getName();

    /**
     * The trace logger for this class.
     */
    private static final Logger trcLogger = WIMLogger.getTraceLogger(CLASSNAME);

    //---------------------------------------------------------------
    // P R I V A T E    V A R I A B L E S
    //---------------------------------------------------------------
    /**
     * Entity data object to be evaluated.
     */
    private DataObject entity = null;

    /**
     * Matching entity types to be searched.
     */
    private List entityTypes = null;

    /**
     * Node of an XPATH expression tree.
     */
    private XPathNode node = null;

    /**
     * Search bases to match during search.
     */
    private List searchBases = null;

    /**
     * List of login properties of LoginAccount entity.
     */
    private List loginProperties = null;

    /**
     * List indicating whether the login property is multivalued or not.
     */
    private List loginPropertiesTypeMultiValued = null;

    /**
     * Flag indicating if the search is being performed based on principalName
     * property.
     */
    private boolean isPrincipalNameSearch = false;

    /**
     * uniqueName of the entity with matching principalName.
     */
    private String principalUniqueName = null;

    //---------------------------------------------------------------
    // C O N S T R U C T O R S
    //---------------------------------------------------------------
    /**
     * Default constructor.
     */
    public XPathHelper()
    {
    }

    /**
     * Constructor to initialize the class variables.
     * 
     * @param control control dataobject
     * @param loginProperties login properties
     * @param loginPropertiesTypeMultiValued which login properties are
     *            multivalued
     * 
     * @throws WIMException
     */
    public XPathHelper(DataObject control, List loginProperties,
        List loginPropertiesTypeMultiValued) throws WIMException
    {
        this(control.getString(SchemaConstants.PROP_SEARCH_EXPRESSION),
            control.getList(SchemaConstants.PROP_SEARCH_BASES),
            loginProperties, loginPropertiesTypeMultiValued);
    }
    
    /**
     * Constructor to initialize the class variables.
     * 
     * @param searchExpr search expression
     * @param searchBases search bases
     * @param loginProperties login properties
     * @param loginPropertiesTypeMultiValued which login properties are
     *            multivalued
     * 
     * @throws WIMException
     */
    public XPathHelper(String searchExpr, List searchBases,
        List loginProperties, List loginPropertiesTypeMultiValued)
        throws WIMException
    {
        final String METHODNAME = "<init>";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME, "searchExpr="
                + searchExpr + ", searchBases=" + searchBases);
        }
        // parse and validate the search expression
        parseSearchExpression(searchExpr);

        this.searchBases = searchBases;
        this.loginProperties = loginProperties;
        this.loginPropertiesTypeMultiValued = loginPropertiesTypeMultiValued;
    }

    /**
     * Return the entity type that should match.
     * 
     * @return List of entity types.
     */
    public List getEntityTypes()
    {
        return entityTypes;
    }

    /**
     * Returns top node of the search expression tree.
     * 
     * @return XPathNode of the search expression tree.
     */
    public XPathNode getNode()
    {
        return node;
    }

    /**
     * Return true if the search is being performed for principalName.
     */
    public boolean isPrincipalNameSearch()
    {
        return isPrincipalNameSearch;
    }

    /** 
     * Parse the XPATH search expression and build a tree.
     * 
     * @param searchExpr search expression to be parsed.
     * 
     * @throws WIMException
     */
    public void parseSearchExpression(String searchExpr) throws WIMException
    {
        final String METHODNAME = "parseSearchExpression";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME, "parsing "
                + searchExpr);
        }

        try {
            // nothing to parse if search string is not set
            if (searchExpr == null || searchExpr.trim().length() == 0) {
                return;
            }

            // parse the search string
            WIMXPathInterpreter parser = new WIMXPathInterpreter(
                new StringReader(searchExpr));
            node = parser.parse(null);

            // get the entity types to be matched
            entityTypes = parser.getEntityTypes();

            // remove the namespace from property names and
            // also validate that valid wildcard is specified
            HashMap propNodes = new HashMap();
            if (node != null) {
                Iterator propNodesItr = node.getPropertyNodes(propNodes);
                while (propNodesItr.hasNext()) {
                    PropertyNode propNode = 
                        (PropertyNode) propNodesItr.next();
                    propNode.setName(removeNamespace(propNode.getName()));
                    String value = (String) propNode.getValue();
                    validatePropertyNode(propNode);

                    countWildcardsAndValidatePattern(value);
                }
            }
        }
        catch (WIMException e) {
            throw e;
        }
        catch (Exception e) {
            throw new WIMApplicationException(
                WIMMessageKey.MALFORMED_SEARCH_EXPRESSION, 
                WIMMessageHelper.generateMsgParms(searchExpr), 
                Level.WARNING, CLASSNAME, METHODNAME, e);
        }
        catch (TokenMgrError e) {
            throw new SearchControlException(
                WIMMessageKey.INVALID_SEARCH_EXPRESSION, 
                WIMMessageHelper.generateMsgParms(searchExpr), 
                CLASSNAME, METHODNAME, e);
        }
    }

    /**
     * Check the search property name for validity and restrictions.
     * 
     * @param node property node
     */
    public void validatePropertyNode(PropertyNode node) throws Exception
    {
        final String METHODNAME = "validatePropertyNode";

        String propName = node.getName();

        // principalName can not be searched with other properties
        if (isPrincipalNameSearch) {
            throw new WIMApplicationException(
                WIMMessageKey.CANNOT_SEARCH_PRINCIPAL_NAME_WITH_OTHER_PROPS,
                Level.WARNING, CLASSNAME, METHODNAME);

        }

        // check if principalName is being searched upon
        if (SchemaConstants.PROP_PRINCIPAL_NAME.equals(propName)) {
            isPrincipalNameSearch = true;
            String value = (String) node.getValue();

            // if searching for principalName as a uniqueName, get the
            if (value != null && !value.trim().equals("")
                && GenericHelper.isDN(value)) {
                principalUniqueName = value;
            }
        }
    }

    /**
     * Validate the wildcards in the search expression pattern. Pattern with
     * more than one wildcard ("*") is not supported unless the pattern contains
     * exactly two wildcards that are at the start and end of the text with
     * non-wildcard text in the middle (e.g. "*a*").
     * 
     * @param pattern value pattern from the search expression
     * @return number of wildcards in a pattern
     * @throws InvalidArgumentException if the pattern is not valid.
     */
    public int countWildcardsAndValidatePattern(String value)
        throws InvalidArgumentException
    {
        int numOfWildcards = 0;

        // count number of wildcards
        try {
            int wildcardIndex = value.indexOf(GenericHelper.WILDCARD);
            if (wildcardIndex != -1) {
                while ((wildcardIndex != -1)
                    && (wildcardIndex + 1) <= value.length()) {
                    numOfWildcards++;
                    wildcardIndex = value.indexOf(GenericHelper.WILDCARD,
                        wildcardIndex + 1);
                }
            }
        }
        catch (Exception e) {
        }

        // if there are more than one wildcard, check its location and validate
        // the pattern
        if (numOfWildcards > 1) {
            boolean valid = false;
            // check if pattern is like *a*
            if (numOfWildcards == 2) {
                if ((value.length() > 2)
                    && (value.startsWith(GenericHelper.WILDCARD))
                    && (value.endsWith(GenericHelper.WILDCARD)))
                    valid = true;
            }
            if (!valid) {
                throw new InvalidArgumentException(
                    WIMMessageKey.INVALID_SEARCH_PATTERN, 
                    WIMMessageHelper.generateMsgParms(value), Level.WARNING, 
                    CLASSNAME, "countWildcardAndValidatePattern");
            }
        }

        return numOfWildcards;
    }

    /**
     * Remove namespace prefix from the entity type and property name. For
     * example, transforms 'wim:PersonAccount' to PersonAccount.
     * 
     * @param qualifiedName name qualified with namespace
     * @return name without the namespace
     */
    //   
    public String removeNamespace(String qualifiedName)
    {
        String str = qualifiedName.replace('\'', ' ').trim();
        int index = str.indexOf(":");
        if (index > 0)
            str = str.substring(index + 1);
        return str;
    }

    /**
     * Set the entity dataobject for which the search will be evaluated.
     * 
     * @param entity entity dataobject to be searched.
     */
    public void setEntity(DataObject entity)
    {
        this.entity = entity;
        if (trcLogger.isLoggable(Level.FINEST))
            trcLogger.logp(Level.FINEST, CLASSNAME, "setEntity",
                "evaluate search expression for "
                    + entity.getString(GenericHelper.UNIQUE_NAME_PATH));
    }

    /**
     * Evaluate the search expression for the entity.
     * 
     * @param entity entity dataobject against which the search expression will
     *            be evaluated.
     */
    public boolean evaluate(DataObject entity) throws WIMException
    {
        setEntity(entity);

        // if entity type is not required to be matched or
        // if search expression is not set, match the search bases
        if ((entityTypes == null) && (node == null)) {
            return matchSearchBase();
        }
        else if (matchEntityType()) {
            // entity type matches, evaluate the search expression or
            // match the search base
            return (node != null) ? evaluateXPathNode(node) :
                matchSearchBase();
        }
        else
            return false;
    }

    /**
     * Evaluate the nodes of the XPath search expression
     * 
     * @param node XPath node
     * @return true if the property-value specified in search expression matches
     *         the properties-value of the entity
     * 
     * @throws WIMException
     */
    public boolean evaluateXPathNode(XPathNode node) throws WIMException
    {
        final String METHODNAME = "evaluateXPathNode";

        try {
            // determine node type
            switch (node.getNodeType()) {
                // evaluate a property node
                case XPathNode.NODE_PROPERTY:
                    return evaluateXPathNode((PropertyNode) node);

                // evaluate a parenthesis node
                case XPathNode.NODE_PARENTHESIS:
                    return evaluateXPathNode((ParenthesisNode) node);

                // evaluate a logical node
                case XPathNode.NODE_LOGICAL:
                    return evaluateXPathNode((LogicalNode) node);

                // an invalid node type
                default:
                    return false;
            }
        }
        catch (WIMException we) {
            throw we;
        }
        catch (Exception e) {
            throw new WIMApplicationException(WIMMessageKey.GENERIC,
                WIMMessageHelper.generateMsgParms(e.getMessage()), CLASSNAME,
                METHODNAME);
        }
    }

    /**
     * Evaluate a property node. Property node will be the leaf node of the
     * search expression tree. Compare the value for the property and return
     * true if it matches.
     * 
     * @param propNode {@link PropertyNode}node
     * @return true if the entity's property-value matches the property-value of
     *         this particular XPath node
     * @throws Exception
     */
    private boolean evaluateXPathNode(PropertyNode propNode) throws Exception
    {
        final String METHODNAME = "evaluateXPathNode(PropertyNode)";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME, "evaluating "
                + propNode);
        }
        boolean match = false;

        // if the search base matches for this entity
        if (matchSearchBase()) {
            try {
                // if this is a principalName search then no other properties
                // are allowed with it
                match = (isPrincipalNameSearch) ?
                    evaluateXPathNodeForPrincipalName(propNode) :
                    evaluateXPathNodeForProperty(propNode);
            }
            catch (Exception e) {
                if (trcLogger.isLoggable(Level.FINE)) {
                    trcLogger.logp(Level.FINE, CLASSNAME, METHODNAME,
                        "Exception occurred:", e);
                }
            }
        }

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.exiting(CLASSNAME, METHODNAME, "match=" + match);
        }

        return match;
    }


    /**
     * Evaluate a logical node. A logical node will contain two children.
     * Evaluate its children nodes first then apply the logical operator. Only
     * supported logical operators are "and" and "or". <br>
     * This method could be called recursively by itself or by Parenthesis node
     * evaluator.
     * 
     * @param logicalNode {@link LogicalNode}node
     * 
     * @return true if the logical node is evaulated to be true.
     * 
     * @throws Exception
     */
    private boolean evaluateXPathNode(LogicalNode logicalNode)
        throws Exception
    {
        final String METHODNAME = "evaluateXPathNode(LogicalNode)";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME, "evaluating "
                + logicalNode);
        }

        try {
            // evaluate the left node
            boolean leftChildResult = evaluateXPathNode((XPathNode) logicalNode
                .getLeftChild());

            // evaluate the right node
            boolean rightChildResult = evaluateXPathNode((XPathNode) logicalNode
                .getRightChild());

            // check the operators and evaluate
            if (logicalNode.getOperator().equalsIgnoreCase("and"))
                return leftChildResult && rightChildResult;
            else if (logicalNode.getOperator().equalsIgnoreCase("or"))
                return leftChildResult || rightChildResult;
            else
                throw new WIMApplicationException("Logical operator "
                    + logicalNode.getOperator() + " is not supported");
        }
        finally {
            if (trcLogger.isLoggable(Level.FINEST)) {
                trcLogger.exiting(CLASSNAME, METHODNAME);
            }
        }
    }

    /**
     * Evaluate the parenthesis node. Parenthesis node will only contain one
     * child node, evaluate that.
     * 
     * @param parenNode {@link ParenthesisNode}node
     * @return true if the evaluation of child node returns true
     * @throws Exception
     */
    private boolean evaluateXPathNode(ParenthesisNode parenNode)
        throws Exception
    {
        final String METHODNAME = "evaluateXPathNode(ParenthesisNode)";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME);
        }

        try {
            // evaluate the child
            return evaluateXPathNode((XPathNode) parenNode.getChild());
        }
        finally {
            if (trcLogger.isLoggable(Level.FINEST))
                trcLogger.exiting(CLASSNAME, METHODNAME);
        }
    }

    /**
     * Evaluate the property node for principalName.
     * 
     * @param propNode {@link PropertyNode} node
     * 
     * @return true if the value of principalName property matches the pattern
     * 
     * @throws Exception
     */
    public boolean evaluateXPathNodeForPrincipalName(PropertyNode propNode)
        throws Exception
    {
        final String METHODNAME = "evaluateXPathNodeForPrincipalName";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME);
        }

        boolean match = false;

        // get propertyname<operator>pattern (for example, principalName=rk*)
        String propName = propNode.getName();
        String operator = propNode.getOperator();
        String pattern = (String) propNode.getValue();

        // if pattern is a uinqueName, match the uniqueName of the entity
        if (principalUniqueName != null) {
            match = patternMatch(
                entity.getString(GenericHelper.UNIQUE_NAME_PATH), operator, 
                pattern);
        }
        else {
            // if principalName is not an uniqueName
            // then match it to one of the login properties
            for (int i = 0; i < loginProperties.size(); i++) {
                String loginProp = (String) loginProperties.get(i);

                // if loginProperty is single valued
                if (Boolean.FALSE.equals(
                    (Boolean) loginPropertiesTypeMultiValued.get(i))) {
                    match = patternMatch(entity.getString(loginProp),
                        operator, pattern);
                    if (match) {
                        break;
                    }
                }
                else {
                    // loginProperty is multi valued
                    List loginValues = entity.getList(loginProp);
                    for (int j = 0; j < loginValues.size(); j++) {
                        match = patternMatch((String) loginValues.get(j),
                            operator, pattern);
                        if (match) {
                            break;
                        }
                    }
                }
            }
        }

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.exiting(CLASSNAME, METHODNAME, "match=" + match);
        }
        return match;
    }

    /**
     * Evaluate the property node for a property.
     * 
     * @param propNode {@link PropertyNode}node
     * @return true if the value of property matches the pattern
     * @throws Exception
     */
    public boolean evaluateXPathNodeForProperty(PropertyNode propNode)
        throws Exception
    {
        final String METHODNAME = "evaluateXPathNodeForProperty";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME);
        }

        boolean match = false;

        // get propertyname<operator>pattern (for example, uid=rk*)
        String propName = propNode.getName();
        String operator = propNode.getOperator();
        String pattern = (String) propNode.getValue();

        // if property is sent then match the pattern with value
        if (entity.isSet(propName)) {
            Object val = entity.get(propName);

            // if value is multivalued
            if (val instanceof List) {
                List values = (List) val;
                // match the pattern against all the values
                // stop matching after first match is found
                for (int i = 0; i < values.size(); i++) {
                    val = values.get(i);
                    if (val instanceof DataObject) {
                        // this should be a reference property, like
                        // "manager" which is of type identifier
                        // match the uniqueName
                        DataObject valDO = (DataObject) val;
                        if (SchemaConstants.DO_IDENTIFIER_TYPE.equals(
                            valDO.getType().getName())) {

                            String valStr = valDO.getString(
                                SchemaConstants.PROP_UNIQUE_NAME);

                            if (valStr == null) {
                                valStr = valDO.getString(
                                    SchemaConstants.PROP_EXTERNAL_NAME);
                            }
                            match = patternMatch(valStr, operator, pattern);
                        }
                    }
                    else {
                        match = patternMatch(val, operator, pattern);
                    }
                    // if a match is found, do not check other values
                    if (match) {
                        break;
                    }
                }
            }
            else {
                // value is singlevalued
                match = patternMatch(val, operator, pattern);
            }
        }

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.exiting(CLASSNAME, METHODNAME, "match=" + match);
        }
        return match;
    }
    
    /**
     * Match the search bases with the entity uniqueName.
     * 
     * @return true if searchBase is not set or if the entity's uniqueName is
     *         not set or if entity's uniqueName ends with the searchBase.
     */
    public boolean matchSearchBase()
    {
        final String METHODNAME = "matchSearchBase";

        boolean match = true;

        // if search base was set in the SearchControl
        if ((searchBases != null) && (searchBases.size() > 0)) {
            String uniqueName = null;
            DataObject identifier = 
                entity.getDataObject(SchemaConstants.DO_IDENTIFIER);
            if (identifier != null)
                uniqueName = 
                    identifier.getString(SchemaConstants.PROP_UNIQUE_NAME);

            // if uniqueName is set, check if it ends with one of the search
            // bases
            if (uniqueName != null) {
                if (trcLogger.isLoggable(Level.FINEST))
                    trcLogger.logp(Level.FINEST, CLASSNAME, METHODNAME,
                        "evaluating uniqueName:" + uniqueName);
                match = false;
                for (int i = 0; i < searchBases.size(); i++) {
                    if (normalizedStringsEndsWith(uniqueName,
                        (String) searchBases.get(i))) {
                        match = true;
                        break;
                    }
                }
            }
        }
        return match;
    }

    /**
     * Match the entity type. Search expression can contain the entity types to
     * be returned.
     * 
     * @return true if the entity type of the entity being evaluated matches or
     *         is a subclass of the entity type set in the search expression.
     */
    public boolean matchEntityType()
    {
        boolean match = false;

        // get the entity type of the entity
        String entityType = entity.getType().getName();

        // check if the entity type is same as superclass of the entity types in
        // search expression
        for (int i = 0; i < entityTypes.size(); i++) {
            if (SchemaHelper.isSuperType((String) entityTypes.get(i),
                entityType)) {
                match = true;
                break;
            }
        }
        return match;
    }

    /**
     * Performs pattern matching. Pattern with more than one wildcard ("*") is
     * not supported unless the pattern contains exactly two wildcards that are
     * at the start and end of the text with non-wildcard text in the middle
     * (e.g. <tt>"*a*"</tt>). Supported operators are:
     * <ul>
     * <li><tt>"<"</tt>
     * <li>
     * <li><tt>">"</tt>
     * <li>
     * <li><tt>"<="</tt>
     * <li>
     * <li><tt>">="</tt>
     * <li>
     * <li><tt>"="</tt>
     * <li>
     * <li><tt>"!="</tt>
     * <li>
     * </ul>
     * </p>
     * <p>
     * If the operator is <tt>"<"</tt>,<tt>">"</tt>,<tt>"<="</tt>,
     * or <tt>">="</tt> then the pattern and value are treated as numeric,
     * otherwise they are treated as String.
     * </p>
     * 
     * @param value The value being checked.
     * @param operator The operator to use for comparison.
     * @param pattern The pattern to match against.
     * @return true if the pattern matches.
     */
    public boolean patternMatch(Object value, String operator, String pattern)
    {
        final String METHODNAME = "patternMatch";

        if (trcLogger.isLoggable(Level.FINEST)) {
            trcLogger.entering(CLASSNAME, METHODNAME, "" + value + " "
                + operator + " " + pattern);
        }

        boolean match = false;
        try {
            if (operator.equals("=") || operator.equals("!=")) {
                // get the string
                String str = (String) value;

                // detect the case where "null == null"
                if (operator.equals("=") && (str == null)
                    && (pattern == null)) {
                    match = true;
                }

                // match the value against the pattern
                else if ((str != null) && (pattern != null)) {
                    // get number of wild cards in the pattern
                    int numOfWildcards = 
                        countWildcardsAndValidatePattern(pattern);

                    // handle the "*a*" case
                    if (numOfWildcards == 2) {
                        // get the contained pattern
                        String subPattern = pattern.substring(1, 
                            pattern.length() - 1);

                        // check if the value contains the contained pattern 
                        if ((operator.equals("=")) && 
                            (normalizedStringContains(str, subPattern) != -1))
                            match = true;
                        else if (operator.equals("!=") && 
                            (normalizedStringContains(str, subPattern) == -1))
                            match = true;
                    }
                    else if (numOfWildcards == 1) {
                        // match pattern like "*", "a*", "*b"
                        int index = pattern.indexOf(GenericHelper.WILDCARD);

                        // get the prefix (substring before *)
                        String prefix = pattern.substring(0, index);

                        // get the suffix (substring after *)
                        String suffix = (index == (pattern.length() - 1))
                            ? "" : pattern.substring(index + 1);

                        // check if the prefix and suffix are present or not
                        // present in the value
                        if ((operator.equals("=")) && 
                            (normalizedStringsStartsWith(str, prefix)) && 
                            (normalizedStringsEndsWith(str, suffix))) {
                            match = true;
                        }
                        else if (operator.equals("!=") && 
                            ((!normalizedStringsStartsWith(str, prefix)) ||
                                (!normalizedStringsEndsWith(str, suffix)))) {
                            match = true;
                        }
                    }
                    else {
                        // no wildcard in the pattern, perform exact match
                        if (operator.equals("=") && 
                            (normalizedStringsAreEqual(str, pattern))) {
                            match = true;
                        }
                        else if (operator.equals("!=") && 
                            (!normalizedStringsAreEqual(str, pattern))) {
                            match = true;
                        }
                    }
                }
            }
            else {
                // convert the value to a number
                try {
                    long longval = Long.parseLong(value.toString());
                    long longpat = Long.parseLong(pattern);

                    // compare the integer values
                    if (operator.equals("<")) {
                        match = (longval < longpat);
                    }
                    else if (operator.equals(">")) {
                        match = (longval > longpat);
                    }
                    else if (operator.equals("<=")) {
                        match = (longval <= longpat);
                    }
                    else if (operator.equals(">=")) {
                        match = (longval >= longpat);
                    }

                }
                catch (NumberFormatException e) {
                    // attempt to compare floating-point values
                    double dblval = Double.parseDouble(value.toString());
                    double dblpat = Double.parseDouble(pattern);

                    // compare the floating-point values
                    if (operator.equals("<")) {
                        match = (dblval < dblpat);
                    }
                    else if (operator.equals(">")) {
                        match = (dblval > dblpat);
                    }
                    else if (operator.equals("<=")) {
                        match = (dblval <= dblpat);
                    }
                    else if (operator.equals(">=")) {
                        match = (dblval >= dblpat);
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (trcLogger.isLoggable(Level.FINEST)) {
                trcLogger.exiting(CLASSNAME, METHODNAME, "match=" + match);
            }
        }
        return match;
    }

    /**
     * Do a case insensitive comparison and return true if two strings are
     * equal.
     * 
     * @param str1 a string (non-null)
     * @param str2 another string
     */
    public boolean normalizedStringsAreEqual(String str1, String str2)
    {
        return str1.equalsIgnoreCase(str2);
    }

    /**
     * Do a case insensitive comparison and return true if one string starts
     * with another.
     * 
     * @param str1 a string (non-null)
     * @param prefix the prefix string (non-null)
     */
    public boolean normalizedStringsStartsWith(String str1, String prefix)
    {
        return str1.toLowerCase().startsWith(prefix.toLowerCase());
    }

    /**
     * Do a case insensitive comparison and return true if one string ends with
     * another.
     * 
     * @param str1 a string (non-null)
     * @param suffix the prefix string (non-null)
     */
    public boolean normalizedStringsEndsWith(String str1, String suffix)
    {
        return str1.toLowerCase().endsWith(suffix.toLowerCase());
    }

    /**
     * Do a case insensitive comparison and return true if one string contains
     * another string.
     * 
     * @param str1 a string (non-null)
     * @param containedStr string (non-null)
     */
    public int normalizedStringContains(String str1, String containedStr)
    {
        return str1.toLowerCase().indexOf(containedStr.toLowerCase());
    }
}