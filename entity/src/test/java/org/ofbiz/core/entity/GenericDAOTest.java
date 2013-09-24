package org.ofbiz.core.entity;

import com.google.common.collect.ImmutableList;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.ofbiz.core.entity.config.DatasourceInfo;
import org.ofbiz.core.entity.model.ModelEntity;
import org.ofbiz.core.entity.model.ModelField;
import org.ofbiz.core.entity.model.ModelFieldTypeReader;
import org.ofbiz.core.entity.model.ModelViewEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.ofbiz.core.entity.EntityOperator.AND;
import static org.ofbiz.core.entity.EntityOperator.IN;
import static org.ofbiz.core.entity.EntityOperator.OR;

public class GenericDAOTest {

    private static final String HELPER_NAME = "MyHelper";
    private static final String TABLE_NAME = "Issue";

    @Mock private CountHelper mockCountHelper;
    @Mock private DatasourceInfo mockDatasourceInfo;
    @Mock private LimitHelper mockLimitHelper;
    @Mock private ModelEntity mockModelEntity;
    @Mock private ModelFieldTypeReader mockModelFieldTypeReader;
    private GenericDAO dao;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mockModelEntity.getTableName(mockDatasourceInfo)).thenReturn(TABLE_NAME);
        dao = new GenericDAO(HELPER_NAME, mockModelFieldTypeReader, mockDatasourceInfo, mockLimitHelper, mockCountHelper);
    }

    @Test
    public void testCorrectlyDetectsExpressionToBeRewritten() throws Exception {
        assertTrue(GenericDAO.conditionContainsInClauseWithListOfSizeGreaterThanMaxSize(
                new EntityExpr("test", IN, ImmutableList.of(1, 2)), 1));
        assertFalse(GenericDAO.conditionContainsInClauseWithListOfSizeGreaterThanMaxSize(
                new EntityExpr("test", IN, ImmutableList.of(1, 2)), 5));
        assertFalse(GenericDAO.conditionContainsInClauseWithListOfSizeGreaterThanMaxSize(
                new EntityExpr("test", AND, ImmutableList.of(1, 2)), 1));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCorrectlyRewritesQuery() throws Exception {
        EntityCondition result = GenericDAO.transformConditionSplittingInClauseListsToChunksNoLongerThanMaxSize(
                new EntityExpr("test", IN, ImmutableList.of(1, 2, 3, 4, 5)), 2);

        assertThat(result, instanceOf(EntityExprList.class));
        assertThat(((EntityExprList) result).getExprListSize(), equalTo(3));
        assertThat(((EntityExprList) result).getOperator(), equalTo(OR));
        final List<EntityExpr> exprList = ImmutableList.copyOf(((EntityExprList) result).getExprIterator());
        assertThat(exprList, contains(
                entityExpr("test", IN, ImmutableList.of(1, 2)),
                entityExpr("test", IN, ImmutableList.of(3, 4)),
                entityExpr("test", IN, ImmutableList.of(5))));
    }

    @Test
    public void testCorrectlyRewritesQueryWithoutModification() throws Exception {
        EntityCondition result = GenericDAO.transformConditionSplittingInClauseListsToChunksNoLongerThanMaxSize(
                new EntityExpr("test", IN, ImmutableList.of(1, 2, 3, 4, 5)), 10);

        assertThat(result, instanceOf(EntityExpr.class));
        assertThat((EntityExpr) result, entityExpr("test", IN, ImmutableList.of(1, 2, 3, 4, 5)));
    }

    private Matcher<EntityExpr> entityExpr(
            final String lhs, final EntityOperator operator, final ImmutableList<Integer> rhs)
    {
        return new BaseMatcher<EntityExpr>() {
            public boolean matches(Object o) {
                return o instanceof EntityExpr
                        && ((EntityExpr) o).getLhs().equals(lhs)
                        && ((EntityExpr) o).getOperator().equals(operator)
                        && Matchers.contains(rhs.toArray()).matches(((EntityExpr) o).getRhs());
            }

            public void describeTo(Description description) {
                description.appendText(new EntityExpr(lhs, operator, rhs).toString());
            }
        };
    }

    @Test
    public void shouldBeAbleToSelectAllColumnsUsingNullListOfSelectFields() throws Exception {
        // Set up
        final EntityFindOptions mockFindOptions = mock(EntityFindOptions.class);

        // Invoke
        final String sql = dao.getSelectQuery(null, mockFindOptions, mockModelEntity, null, null, null, null, null);

        // Check
        assertEquals("SELECT * FROM " + TABLE_NAME, sql);
    }

    @Test
    public void shouldBeAbleToSelectSpecificFields() throws Exception {
        // Set up
        final EntityFindOptions mockFindOptions = mock(EntityFindOptions.class);
        final ModelField mockFirstNameField = mock(ModelField.class);
        final ModelField mockLastNameField = mock(ModelField.class);
        final List<ModelField> selectFields = asList(mockFirstNameField, mockLastNameField);
        when(mockModelEntity.colNameString(selectFields, ", ", "")).thenReturn("FIRST_NAME, LAST_NAME");

        // Invoke
        final String sql = dao.getSelectQuery(
                selectFields, mockFindOptions, mockModelEntity, null, null, null, null, null);

        // Check
        assertEquals("SELECT FIRST_NAME, LAST_NAME FROM " + TABLE_NAME, sql);
    }

    @Test
    public void shouldBeAbleToSelectDistinctValues() throws Exception {
        // Set up
        final EntityFindOptions mockFindOptions = mock(EntityFindOptions.class);
        final ModelField mockFirstNameField = mock(ModelField.class);
        final List<ModelField> selectFields = singletonList(mockFirstNameField);
        when(mockModelEntity.colNameString(selectFields, ", ", "")).thenReturn("FIRST_NAME");
        when(mockFindOptions.getDistinct()).thenReturn(true);

        // Invoke
        final String sql = dao.getSelectQuery(
                selectFields, mockFindOptions, mockModelEntity, null, null, null, null, null);

        // Check
        assertEquals("SELECT DISTINCT FIRST_NAME FROM " + TABLE_NAME, sql);
    }

    @Test
    public void shouldBeAbleToProvideEntityWhereCondition() throws Exception {
        // Set up
        final EntityFindOptions mockFindOptions = mock(EntityFindOptions.class);
        final ModelField mockFirstNameField = mock(ModelField.class);
        final List<ModelField> selectFields = singletonList(mockFirstNameField);
        when(mockModelEntity.colNameString(selectFields, ", ", "")).thenReturn("FIRST_NAME");
        when(mockFindOptions.getDistinct()).thenReturn(true);
        final EntityCondition mockWhereCondition = mock(EntityCondition.class);
        final List<EntityConditionParam> whereConditionParams = new ArrayList<EntityConditionParam>();
        when(mockWhereCondition.makeWhereString(mockModelEntity, whereConditionParams)).thenReturn("LAST_NAME IS NULL");

        // Invoke
        final String sql = dao.getSelectQuery(
                selectFields, mockFindOptions, mockModelEntity, null, mockWhereCondition, null, whereConditionParams, null);

        // Check
        assertEquals("SELECT DISTINCT FIRST_NAME FROM " + TABLE_NAME + " WHERE LAST_NAME IS NULL", sql);
    }

    @Test
    public void shouldBeAbleToGroupByFields() throws Exception {
        // Set up
        final ModelViewEntity mockModelViewEntity = mock(ModelViewEntity.class);
        final EntityFindOptions mockFindOptions = mock(EntityFindOptions.class);
        @SuppressWarnings("unchecked")
        final List<ModelField> mockGroupBysCopy = mock(List.class);
        when(mockModelViewEntity.getGroupBysCopy()).thenReturn(mockGroupBysCopy);
        final String groupByString = "LAST_NAME";
        when(mockModelViewEntity.colNameString(mockGroupBysCopy, ", ", "")).thenReturn(groupByString);
        when(mockDatasourceInfo.getJoinStyle()).thenReturn("theta-oracle");
        final Map<String, ModelViewEntity.ModelMemberEntity> memberModelMemberEntities = emptyMap();
        when(mockModelViewEntity.getMemberModelMemberEntities()).thenReturn(memberModelMemberEntities);

        // Invoke
        final String sql = dao.getSelectQuery(
                null, mockFindOptions, mockModelViewEntity, null, null, null, null, null);

        // Check (invalid SQL, but creating valid SQL requires lots of test setup and doesn't test the DAO)
        assertEquals("SELECT * FROM  GROUP BY LAST_NAME", sql);
    }

    @Test
    public void shouldBeAbleToApplyHavingCondition() throws Exception {
        // Set up
        final EntityFindOptions mockFindOptions = mock(EntityFindOptions.class);
        final EntityCondition mockHavingEntityCondition = mock(EntityCondition.class);
        final List<EntityConditionParam> havingConditionParams = new ArrayList<EntityConditionParam>();
        when(mockHavingEntityCondition.makeWhereString(mockModelEntity, havingConditionParams)).thenReturn("BLAH");

        // Invoke
        final String sql = dao.getSelectQuery(null, mockFindOptions, mockModelEntity, null, null,
                mockHavingEntityCondition, null, havingConditionParams);

        // Check (invalid SQL, but creating valid SQL requires lots of test setup and doesn't test the DAO)
        assertEquals("SELECT * FROM Issue HAVING BLAH", sql);
    }

    @Test
    public void shouldBeAbleToRetrieveAGivenPageOfResults() throws Exception {
        // Set up
        final ModelField mockSelectField = mock(ModelField.class);
        final EntityFindOptions mockFindOptions = mock(EntityFindOptions.class);
        final int maxResults = 40;
        final int offset = 2000;
        when(mockFindOptions.getMaxResults()).thenReturn(maxResults);
        when(mockFindOptions.getOffset()).thenReturn(offset);
        final List<ModelField> selectFields = singletonList(mockSelectField);
        when(mockModelEntity.colNameString(selectFields, ", ", "")).thenReturn("Address");
        final String sqlWithLimit = "some SQL with a limit";
        when(mockLimitHelper.addLimitClause("SELECT Address FROM Issue", selectFields, offset, maxResults))
                .thenReturn(sqlWithLimit);

        // Invoke
        final String sql =
                dao.getSelectQuery(selectFields, mockFindOptions, mockModelEntity, null, null, null, null, null);

        // Check
        assertEquals(sqlWithLimit, sql);
    }

    @Test
    public void shouldBeAbleToSelectForUpdate() throws Exception {
        // Set up
        final EntityFindOptions mockFindOptions = mock(EntityFindOptions.class);
        when(mockFindOptions.isForUpdate()).thenReturn(true);

        // Invoke
        final String sql = dao.getSelectQuery(null, mockFindOptions, mockModelEntity, null, null, null, null, null);

        // Check
        assertEquals("SELECT * FROM Issue FOR UPDATE", sql);
    }

    @Test
    public void storeAllShouldAcceptNullEntityList() throws Exception {
        assertEquals(0, dao.storeAll(null));
    }

    @Test
    public void storeAllShouldAcceptEmptyEntityList() throws Exception {
        assertEquals(0, dao.storeAll(Collections.<GenericEntity>emptyList()));
    }
}
