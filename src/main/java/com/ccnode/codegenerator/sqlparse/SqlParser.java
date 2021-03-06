package com.ccnode.codegenerator.sqlparse;

import com.ccnode.codegenerator.constants.MyBatisXmlConstants;
import com.ccnode.codegenerator.util.PsiClassUtil;
import com.ccnode.codegenerator.view.completion.MysqlCompleteCacheInteface;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlTag;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author bruce.ge
 * @Date 2017/3/9
 * @Description
 */
public class SqlParser {
    public static ParsedResult parse(ParseContext context) {
        //first make it parse to a list of word.
        List<String> baseRecommends = Lists.newArrayList();
        ParsedResult result = new ParsedResult();
        result.setRecommedValues(new ArrayList<>());
        String currentWordStart = context.getCurrentWordStart();
        String beforeCurrentWordString = getBeforeRealString(currentWordStart);
        List<String> beforeWord = extractWords(context.getBeforeText().toLowerCase());
        List<String> afterWords = extractWords(context.getAfterText().toLowerCase());
        boolean currentIsSkipChar = isSkipChar(context.getAllText().charAt(context.getCursorOffSet() - 1));
        if (beforeWord.size() == 0 || (beforeWord.size() == 1 && !currentIsSkipChar)) {
            baseRecommends.add("insert into ");
            baseRecommends.add("select ");
            baseRecommends.add("update ");
            baseRecommends.add("delete ");
            result.setRecommedValues(convertToRecommeds(baseRecommends));
            return result;
        }

        //if not contains from.
        MysqlCompleteCacheInteface cacheService = ServiceManager.getService(context.getProject(), MysqlCompleteCacheInteface.class);
        if (beforeWord.contains("select")) {
            if (checkTableNameRecommend(beforeWord, currentIsSkipChar)) {
                List<String> allTables = cacheService.getAllTables();
                result.setRecommedValues(convertToRecommeds(allTables));
                return result;
            }



            boolean beforeContainsFrom = beforeWord.contains("from");
            boolean afterContainsFrom = afterWords.contains("from");
            //make the render more buautiful.
            if (!beforeContainsFrom) {
                if (checkAsRecommed(beforeWord, currentIsSkipChar)) {
                    //try to extract the resultType.
                    //the before is xml. get from resultType or resultMap.
                    XmlTag currentTag =
                            context.getCurrentTag();
                    String attributeValue = currentTag.getAttributeValue(MyBatisXmlConstants.RESULT_TYPE);
                    if (StringUtils.isNotEmpty(attributeValue)){
                        PsiClass classOfQuatifiedType = PsiClassUtil.findClassOfQuatifiedType(ModuleUtilCore.findModuleForPsiElement(context.getCurrentXmlFile()), context.getProject(), attributeValue);
                        if(classOfQuatifiedType!=null){
                            List<String> strings = PsiClassUtil.extractProps(classOfQuatifiedType);
                            for (String string : strings) {
                                result.getRecommedValues().add(LookupElementBuilder.create(string).withRenderer(new LookupElementRenderer<LookupElement>() {
                                    @Override
                                    public void renderElement(LookupElement element, LookupElementPresentation presentation) {
                                        presentation.setItemText(string);
                                        presentation.setTypeText(attributeValue);
                                    }
                                }));
                            }
                        }
                    }
                    return result;
                }
                if (!afterContainsFrom) {
                    List<TableNameAndFieldName> allFields = cacheService.getAllFieldsWithTable();
                    for (TableNameAndFieldName field : allFields) {
                        result.getRecommedValues().add(getTableAndFieldElement(beforeCurrentWordString, field));
                    }
                    result.getRecommedValues().add(LookupElementBuilder.create("from "));
                    result.getRecommedValues().add(LookupElementBuilder.create("as "));
                    result.getRecommedValues().addAll(MethodRecommendCache.getRecommends(beforeCurrentWordString));
                    return result;
                } else {
                    //make it happend
                    List<TableNameAndFieldName> fields = getRecommendFromTableFields(afterWords, cacheService);
                    for (TableNameAndFieldName field : fields) {
                        result.getRecommedValues().add(getTableAndFieldElement(beforeCurrentWordString, field));
                    }
                    result.getRecommedValues().addAll(MethodRecommendCache.getRecommends(beforeCurrentWordString));
                    return result;
                }
            } else {
                boolean beforeContainWhereOrOn = beforeWord.contains("where") || beforeWord.contains("on");
                if (checkListContains(beforeWord, "order", "by")) {
                    List<TableNameAndFieldName> fields = getRecommendFromTableFields(beforeWord, cacheService);
                    for (TableNameAndFieldName field : fields) {
                        result.getRecommedValues().add(getTableAndFieldElement(beforeCurrentWordString, field));
                    }
                    result.getRecommedValues().addAll(MethodRecommendCache.getRecommends(beforeCurrentWordString));
                    result.getRecommedValues().add(LookupElementBuilder.create("limit "));
                    return result;
                } else if (checkListContains(beforeWord, "having")) {
                    List<TableNameAndFieldName> fields = getRecommendFromTableFields(beforeWord, cacheService);
                    for (TableNameAndFieldName field : fields) {
                        result.getRecommedValues().add(getTableAndFieldElement(beforeCurrentWordString, field));
                    }
                    result.getRecommedValues().addAll(MethodRecommendCache.getRecommends(beforeCurrentWordString));
                    result.getRecommedValues().add(LookupElementBuilder.create("limit "));
                    result.getRecommedValues().add(LookupElementBuilder.create("order by "));
                    return result;
                } else if (checkListContains(beforeWord, "group", "by")) {
                    List<TableNameAndFieldName> fields = getRecommendFromTableFields(beforeWord, cacheService);
                    for (TableNameAndFieldName field : fields) {
                        result.getRecommedValues().add(getTableAndFieldElement(beforeCurrentWordString, field));
                    }
                    result.getRecommedValues().add(LookupElementBuilder.create("order by "));
                    result.getRecommedValues().add(LookupElementBuilder.create("limit "));
                    result.getRecommedValues().add(LookupElementBuilder.create("having "));
                    return result;
                } else if (beforeContainWhereOrOn) {
                    List<TableNameAndFieldName> fields = getRecommendFromTableFields(beforeWord, cacheService);
                    for (TableNameAndFieldName field : fields) {
                        result.getRecommedValues().add(getTableAndFieldElement(beforeCurrentWordString, field));
                    }
                    result.getRecommedValues().add(LookupElementBuilder.create("order by "));
                    result.getRecommedValues().add(LookupElementBuilder.create("limit "));
                    result.getRecommedValues().add(LookupElementBuilder.create("having "));
                    result.getRecommedValues().add(LookupElementBuilder.create("group by "));
                    return result;
                } else {
                    baseRecommends = Lists.newArrayList();
                    baseRecommends.add("inner join ");
                    baseRecommends.add("left join ");
                    baseRecommends.add("right join ");
                    baseRecommends.add("where ");
                    if (beforeWord.contains("join")) {
                        baseRecommends.add("on ");
                    }
                    result.getRecommedValues().addAll(convertToRecommeds(baseRecommends));
                    result.getRecommedValues().add(LookupElementBuilder.create("order by "));
                    result.getRecommedValues().add(LookupElementBuilder.create("limit "));
                    result.getRecommedValues().add(LookupElementBuilder.create("having "));
                    result.getRecommedValues().add(LookupElementBuilder.create("group by "));
                    return result;
                }
            }


            //does it contains where
//            String lastWord = beforeWord.get(beforeWord.size() - 1);
//            String lastSecondWord = beforeWord.get(beforeWord.size() - 2);
//            if (lastWord.equals("from") || (lastSecondWord.equals("from")&&currentIsSkipChar)) {
//                List<String> allTables = cacheService.getAllTables();
//                result.setRecommedValues(allTables);
//            }
//            return result;
        }
        return result;
    }

    private static boolean checkAsRecommed(List<String> beforeWord, boolean currentIsSkipChar) {
        int size = beforeWord.size();
        if (size >= 2) {
            if (currentIsSkipChar) {
                if (beforeWord.get(size - 1).equals("as")) {
                    return true;
                }
            } else {
                if (beforeWord.get(size - 2).equals("as")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean checkListContains(List<String> beforeWord, String... contains) {
        for (String s : contains) {
            if (!beforeWord.contains(s)) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkTableNameRecommend(List<String> beforeWord, boolean currentIsSkipChar) {
        int size = beforeWord.size();
        if (size >= 2) {
            if (currentIsSkipChar) {
                if (beforeWord.get(size - 1).equals("join") || beforeWord.get(size - 1).equals("from")) {
                    return true;
                }
            } else {
                if (beforeWord.get(size - 2).equals("join") || beforeWord.get(size - 2).equals("from")) {
                    return true;
                }
            }
        }
        return false;
    }

    @NotNull
    private static LookupElementBuilder getTableAndFieldElement(String beforeCurrentWordString, final TableNameAndFieldName field) {
        return LookupElementBuilder.create(beforeCurrentWordString + field.getFieldName()).withRenderer(new LookupElementRenderer<LookupElement>() {
            @Override
            public void renderElement(LookupElement element, LookupElementPresentation presentation) {
                presentation.setItemText(field.getFieldName() + "  (" + field.getTableName() + ")");
            }
        });
    }

    private static List<LookupElement> convertToRecommeds(List<String> baseRecommends) {
        List<LookupElement> lookupElements = Lists.newArrayList();
        for (String baseRecommend : baseRecommends) {
            lookupElements.add(LookupElementBuilder.create(baseRecommend));
        }
        return lookupElements;
    }


    private static String getBeforeRealString(String currentWordStart) {
        for (int i = currentWordStart.length() - 1; i >= 0; i--) {
            if (isSkipChar(currentWordStart.charAt(i))) {
                return currentWordStart.substring(0, i + 1);
            }
        }
        return "";
    }

    private static List<TableNameAndFieldName> getRecommendFromTableFields(List<String> words, MysqlCompleteCacheInteface cacheInteface) {
        List<TableNameAndFieldName> recommends = Lists.newArrayList();
        //
        List<TableNameAndAliaseName> tableNameAndAliaseNames = extractNameFrom(words);
        for (TableNameAndAliaseName tableNameAndAliaseName : tableNameAndAliaseNames) {
            if (tableNameAndAliaseName.getAliaseName() == null) {
                recommends.addAll(cacheInteface.getTableAllFields(tableNameAndAliaseName.getTableName()));
            } else {
                List<TableNameAndFieldName> tableAllFields = cacheInteface.getTableAllFields(tableNameAndAliaseName.getTableName());
                for (TableNameAndFieldName tableAllField : tableAllFields) {
                    String s = tableNameAndAliaseName.getAliaseName() + "." + tableAllField.getFieldName();
                    TableNameAndFieldName tableNameAndFieldName = new TableNameAndFieldName();
                    tableNameAndFieldName.setFieldName(s);
                    tableNameAndFieldName.setTableName(tableAllField.getTableName());
                    recommends.add(tableNameAndFieldName);
                }
            }
        }

        //todo extract it to make it work.
        return recommends;
    }

    private static List<TableNameAndAliaseName> extractNameFrom(List<String> words) {
        List<TableNameAndAliaseName> tableNameAndAliaseNames = Lists.newArrayList();
        int size = words.size();
        for (int i = 0; i < words.size(); i++) {
            String s = words.get(i);
            if (s.equals("from") || s.equals("join")) {
                if (i < size - 1) {
                    TableNameAndAliaseName tableNameAndAliaseName = new TableNameAndAliaseName();
                    String tableName = words.get(i + 1);
                    tableNameAndAliaseName.setTableName(tableName);
                    if (i < size - 2) {
                        String aliase = words.get(i + 2);
                        if (aliase.equals("inner") || aliase.equals("left") || aliase.equals("right") || aliase.equals("where") || aliase.equals("on")) {
                            //not do any thing
                        } else if (aliase.equals("as")) {
                            if (i < size - 3) {
                                tableNameAndAliaseName.setAliaseName(words.get(i + 3));
                            }
                        } else {
                            tableNameAndAliaseName.setAliaseName(aliase);
                        }
                    }
                    tableNameAndAliaseNames.add(tableNameAndAliaseName);
                }
            }
        }
        return tableNameAndAliaseNames;
    }


    private static List<String> extractWords(String startText) {
        List<String> words = Lists.newArrayList();
        String m = "";
        for (int i = 0; i < startText.length(); i++) {
            char c = startText.charAt(i);
            if (isSkipChar(c)) {
                if (m.length() > 0) {
                    words.add(m);
                    m = "";
                }
            } else {
                m += c;
            }
        }
        if (m.length() > 0) {
            words.add(m);
        }
        return words;
    }

    private static boolean isSkipChar(char c) {
        return c == ' ' || c == '\n' || c == '\t' || c == ',' || c == '(' || c == ')';
    }
}
