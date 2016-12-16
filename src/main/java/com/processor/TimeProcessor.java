package com.processor;

import com.annotation.Time;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.code.TypeTag;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Set;

@SupportedAnnotationTypes(value = {TimeProcessor.ANNOTATION_TYPE})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class TimeProcessor extends AbstractProcessor {

    public static final String ANNOTATION_TYPE = "com.annotation.Time";
    private JavacProcessingEnvironment javacProcessingEnv;
    private TreeMaker maker;

    @Override
    public void init(ProcessingEnvironment procEnv) {
        super.init(procEnv);
        this.javacProcessingEnv = (JavacProcessingEnvironment) procEnv;
        this.maker = TreeMaker.instance(javacProcessingEnv.getContext());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if (annotations == null || annotations.isEmpty()) {
            return false;
        }

        final Elements elements = javacProcessingEnv.getElementUtils();

        final TypeElement annotation = elements.getTypeElement(Time.class.getName());

        javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "annotation: " +annotation.toString());
        if (annotation != null) {
            // Выбираем все элементы, у которых стоит наша аннотация
            final Set<? extends Element> methods = roundEnv.getElementsAnnotatedWith(annotation);
            javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "methods: " + methods.toString());
            JavacElements utils = javacProcessingEnv.getElementUtils();
            for (final Element m : methods) {
                javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, m.toString());
                createClass(m);

                Time time = m.getAnnotation(Time.class);
                if (time != null) {
                    JCTree blockNode = utils.getTree(m);
                    // Нам нужны только описания методов
                    if (blockNode instanceof JCMethodDecl) {
                        // Получаем содержимое метода
                        final List<JCStatement> statements = ((JCMethodDecl) blockNode).body.stats;

                        // Новое тело метода
                        List<JCStatement> newStatements = List.nil();
                        // Добавляем в начало метода сохранение текущего времени
                        JCVariableDecl var = makeTimeStartVar(maker, utils, time);
                        newStatements = newStatements.append(var);
                        javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "var" + var.toString());

                        // Создаём тело блока try, копируем в него оригинальное содержимое метода
                        List<JCStatement> tryBlock = List.nil();
                        for (JCStatement statement : statements) {
                            tryBlock = tryBlock.append(statement);
                        }

                        // Создаём тело блока finally, добавляем в него вывод затраченного времени
                        JCBlock finalizer = makePrintBlock(maker, utils, time, var);
                        JCStatement stat = maker.Try(maker.Block(0, tryBlock), List.nil(), finalizer);
                        newStatements = newStatements.append(stat);

                        // Заменяем старый код метода на новый
                        ((JCMethodDecl) blockNode).body = maker.Block(0, newStatements);
                    }
                }
            }

            return true;
        }

        return false;
    }

    private void createClass(Element annotatedElement){
        TypeElement clazz = (TypeElement) annotatedElement.getEnclosingElement();
        try {
            JavaFileObject f = processingEnv.getFiler().
                    createSourceFile(clazz.getQualifiedName() + "Autogenerate");
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Creating " + f.toUri());
            Writer w = f.openWriter();
            try {
                String pack = clazz.getQualifiedName().toString();
                PrintWriter pw = new PrintWriter(w);
                pw.println("package "
                        + pack.substring(0, pack.lastIndexOf('.')) + ";");
                pw.println("\npublic class "
                        + clazz.getSimpleName() + "Autogenerate {");

                TypeMirror type = annotatedElement.asType();


                pw.println("\n    protected " + clazz.getSimpleName()
                        + "Autogenerate() {}");
                pw.println("\n    /** Handle something. */");
                pw.println("\n//" + annotatedElement);
                pw.println("    }");
                pw.flush();
            } finally {
                w.close();
            }
        } catch (IOException x) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    x.toString());
        }
    }

    private JCExpression makeCurrentTime(TreeMaker maker, JavacElements utils, Time time) {
        // Создаём вызов System.nanoTime или System.currentTimeMillis
        JCExpression exp = maker.Ident(utils.getName("System"));
        String methodName;
        switch (time.interval()) {
            case NANOSECOND:
                methodName = "nanoTime";
                break;
            default:
                methodName = "currentTimeMillis";
                break;
        }
        exp = maker.Select(exp, utils.getName(methodName));
        return maker.Apply(List.<JCExpression>nil(), exp, List.<JCExpression>nil());
    }

    protected JCVariableDecl makeTimeStartVar(TreeMaker maker, JavacElements utils, Time time) {
        // Создаём финальную переменную для хранения времени старта. Имя переменной в виде time_start_{random}
        JCExpression currentTime = makeCurrentTime(maker, utils, time);
        String fieldName = fieldName = "time_start_" + (int) (Math.random() * 10000);
        return maker.VarDef(maker.Modifiers(Flags.FINAL), utils.getName(fieldName), maker.TypeIdent(TypeTag.LONG), currentTime);
    }

    protected JCBlock makePrintBlock(TreeMaker maker, JavacElements utils, Time time, JCVariableDecl var) {
        // Создаём вызов System.out.println
        JCExpression printlnExpression = maker.Ident(utils.getName("System"));
        printlnExpression = maker.Select(printlnExpression, utils.getName("out"));
        printlnExpression = maker.Select(printlnExpression, utils.getName("println"));

        // Создаём блок вычисления затраченного времени (currentTime - startTime)
        JCExpression currentTime = makeCurrentTime(maker, utils, time);
        JCExpression elapsedTime = maker.Binary(Tag.MINUS, currentTime, maker.Ident(var.name));

        // Форматируем результат
        JCExpression formatExpression = maker.Ident(utils.getName("String"));
        formatExpression = maker.Select(formatExpression, utils.getName("format"));

        // Собираем все кусочки вместе
        List<JCExpression> formatArgs = List.nil();
        formatArgs = formatArgs.append(maker.Literal(time.format()));
        formatArgs = formatArgs.append(elapsedTime);


        JCExpression format = maker.Apply(List.<JCExpression>nil(), formatExpression, formatArgs);
        javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "format " + format.toString());

        List<JCExpression> printlnArgs = List.nil();
        printlnArgs = printlnArgs.append(format);
        javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "printlnArgs " + printlnArgs.toString());

        JCExpression print = maker.Apply(List.<JCExpression>nil(), printlnExpression, printlnArgs);
        javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "print  " + print.toString());

        JCExpressionStatement stmt = maker.Exec(print);
        javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "stmt  " + stmt.toString());

        List<JCStatement> stmts = List.nil();
        stmts = stmts.append(stmt);
        javacProcessingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "stmts  " + stmts.toString());
        return maker.Block(0, stmts);
    }

}
