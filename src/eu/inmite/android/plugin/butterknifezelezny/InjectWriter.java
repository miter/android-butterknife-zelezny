package eu.inmite.android.plugin.butterknifezelezny;

import com.intellij.codeInsight.actions.ReformatAndOptimizeImportsProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
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
     * 为adapter创建ViewHolder和注解
     */
    protected void generateAdapter() {
        // view holder class
        StringBuilder holderBuilder = new StringBuilder();
        holderBuilder.append(mHolderClassName);
        holderBuilder.append("(android.view.View view) {");
        holderBuilder.append(mMethodName);
        holderBuilder.append("(view);");
        holderBuilder.append("}");

        //创建一个类
        PsiClass viewHolder = mFactory.createClassFromText(holderBuilder.toString(), mClass);
        viewHolder.setName(mHolderClassName);

        List<StringBuilder> fields = createViewFields();
        for (StringBuilder field : fields) {
            viewHolder.add(mFactory.createFieldFromText(field.toString(), viewHolder));
        }
        viewHolder.add(mFactory.createMethodFromText(createMethod(mMethodName).toString(), viewHolder));
        mClass.add(viewHolder);
        // add view holder's comment
        //类说明
        StringBuilder comment = new StringBuilder();

        comment.append("/**\n");
        comment.append(" * This class contains all butterknife-injected Views & Layouts from layout file '");
        comment.append(mLayoutFileName);
        comment.append("'\n");
        comment.append("* for easy to all layout elements.\n");
        comment.append(" *\n");
        comment.append(" * @author\tAndroid Butter Zelezny, plugin for IntelliJ IDEA/Android Studio by Inmite (www.inmite.eu)\n");
        comment.append("*/");

        mClass.addBefore(mFactory.createCommentFromText(comment.toString(), mClass), mClass.findInnerClassByName(mHolderClassName, true));
        mClass.addBefore(mFactory.createKeyword("static", mClass), mClass.findInnerClassByName(mHolderClassName, true));
    }

    /**
     * 创建一个初始化View的方法
     *
     * @param methodName 方法名
     * @return
     */
    private StringBuilder createMethod(String methodName) {
        //创建一个方法
        StringBuilder method = new StringBuilder();
        method.append("private void ");
        method.append(methodName);
        method.append("(android.view.View root)");
        method.append("{");
        method.append(createInitViews());
        method.append("}");
        return method;
    }

    /**
     * 生成View初始化内容
     *
     * @return
     */
    private StringBuilder createInitViews() {
        StringBuilder s = new StringBuilder();
        for (Element element : mElements) {
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
            s.append(")root.findViewById(");
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
        mClass.add(mFactory.createMethodFromText(createMethod(mMethodName).toString(), mClass));
    }
}
