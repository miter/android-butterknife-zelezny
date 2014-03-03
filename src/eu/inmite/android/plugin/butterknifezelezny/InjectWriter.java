package eu.inmite.android.plugin.butterknifezelezny;

import com.intellij.codeInsight.actions.ReformatAndOptimizeImportsProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import eu.inmite.android.plugin.butterknifezelezny.common.Defintions;
import eu.inmite.android.plugin.butterknifezelezny.model.Element;

import java.util.ArrayList;
import java.util.List;

public class InjectWriter extends WriteCommandAction.Simple {

    protected PsiFile mFile;
    protected Project mProject;
    protected PsiClass mClass;
    protected ArrayList<Element> mElements;
    protected PsiElementFactory mFactory;
    protected String mLayoutFileName;
    protected String mFieldNamePrefix;
    protected boolean mCreateHolder;
    protected String mHolderClassName;
    //
//    public static final String sViewHolderName = "ButterknifeViewHolder";
    protected String mMethodName;
    protected boolean hasFindViewMethod = false;

    public InjectWriter(PsiFile file, PsiClass clazz, String command, ArrayList<Element> elements, String layoutFileName, String fieldNamePrefix, boolean createHolder, String holderClassName, String methodName) {
        super(clazz.getProject(), command);

        mFile = file;
        mProject = clazz.getProject();
        mClass = clazz;
        mElements = elements;
        mFactory = JavaPsiFacade.getElementFactory(mProject);
        mLayoutFileName = layoutFileName;
        mFieldNamePrefix = fieldNamePrefix;
        mCreateHolder = createHolder;
        mHolderClassName = holderClassName;
        mMethodName = methodName;
        hasFindViewMethod = checkHasFindViewMethod(mClass);
    }

    /**
     * 检查该类中是否存在findViewById方法
     *
     * @param psiClass
     */
    private boolean checkHasFindViewMethod(PsiClass psiClass) {
        PsiMethod[] methods = psiClass.findMethodsByName("findViewById", true);
        if (methods != null && methods.length > 0) {
            PsiMethod method = methods[0];
            //TODO:需要检查返回类型
            return true;
        }
        return false;
    }

    @Override
    public void run() throws Throwable {
        if (mCreateHolder) {
            generateAdapter();
        } else {
            generateFields();
        }
        // reformat class
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(mProject);
        styleManager.optimizeImports(mFile);
        styleManager.shortenClassReferences(mClass);

        new ReformatAndOptimizeImportsProcessor(mProject, mClass.getContainingFile(), false).runWithoutProgress();
    }

    /**
     * Create ViewHolder for adapters with injections
     */
    protected void generateAdapter() {
        // view holder class
        StringBuilder holderBuilder = new StringBuilder();
        holderBuilder.append(mHolderClassName);
        holderBuilder.append("(android.view.View view) {");
        holderBuilder.append(mMethodName);
        holderBuilder.append("(view);");
        holderBuilder.append("}");

        PsiClass viewHolder = mFactory.createClassFromText(holderBuilder.toString(), mClass);
        viewHolder.setName(mHolderClassName);

        List<StringBuilder> fields = createViewFields();
        for (StringBuilder field : fields) {
            viewHolder.add(mFactory.createFieldFromText(field.toString(), viewHolder));
        }
        viewHolder.add(mFactory.createMethodFromText(createMethod(mMethodName).toString(), viewHolder));
        mClass.add(viewHolder);
        mClass.addBefore(mFactory.createKeyword("static", mClass), mClass.findInnerClassByName(mHolderClassName, true));
    }

    /**
     * 创建一个初始化View的方法
     *
     * @param methodName 方法名
     * @return
     */
    private StringBuilder createMethod(String methodName) {
        return createMethod(methodName, "android.view.View root");
    }

    /**
     * @param methodName 方法名
     * @param args       参数
     * @return
     */
    private StringBuilder createMethod(String methodName, String... args) {
        //创建一个方法
        StringBuilder method = new StringBuilder();
        method.append("private void ");
        method.append(methodName);
        method.append("(");
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                method.append(arg);
                if (i != args.length - 1) {
                    method.append(",");
                }
            }
        }
        method.append(")");
        method.append("{");
        method.append(createInitViews(args != null));
        method.append("}");
        return method;
    }

    /**
     * 生成View初始化内容
     *
     * @return
     */
    private StringBuilder createInitViews(boolean hasParams) {
        StringBuilder s = new StringBuilder();
        for (Element element : mElements) {
            //不使用
            if (!element.used) continue;

            s.append(element.fieldName);
            s.append("=(");
            if (element.nameFull != null && element.nameFull.length() > 0) { // custom package+class
                s.append(element.nameFull);
            } else if (Defintions.paths.containsKey(element.name)) { // listed class
                s.append(Defintions.paths.get(element.name));
            } else { // android.widget
                s.append("android.widget.");
                s.append(element.name);
            }
            s.append(")");
            if (hasParams) {
                s.append("root");
                s.append(".");
            }
            s.append("findViewById(");
            String rPrefix;
            if (element.isAndroidNS) {
                rPrefix = "android.R.id.";
            } else {
                rPrefix = "R.id.";
            }
            s.append(rPrefix);
            s.append(element.id);
            s.append(");");
            s.append("\n");
        }
        return s;
    }

    /**
     * 生成字段
     *
     * @return
     */
    private List<StringBuilder> createViewFields() {
        ArrayList<StringBuilder> fields = new ArrayList<StringBuilder>();
        for (Element element : mElements) {
            //不适用
            if (!element.used) continue;
            StringBuilder field = new StringBuilder();
            field.append("private ");
            if (element.nameFull != null && element.nameFull.length() > 0) { // custom package+class
                field.append(element.nameFull);
            } else if (Defintions.paths.containsKey(element.name)) { // listed class
                field.append(Defintions.paths.get(element.name));
            } else { // android.widget
                field.append("android.widget.");
                field.append(element.name);
            }
            field.append(" ");
            field.append(element.fieldName);
            field.append(";");
            fields.add(field);
        }
        return fields;
    }

    /**
     * Create fields for injections inside main class
     */
    protected void generateFields() {
        List<StringBuilder> fields = createViewFields();
        for (StringBuilder field : fields) {
            mClass.add(mFactory.createFieldFromText(field.toString(), mClass));
        }
        if (checkHasFindViewMethod(mClass)) {
            mClass.add(mFactory.createMethodFromText(createMethod(mMethodName, null).toString(), mClass));
        } else {
            mClass.add(mFactory.createMethodFromText(createMethod(mMethodName).toString(), mClass));
        }
    }
}
