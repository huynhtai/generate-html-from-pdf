package com.company;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import static com.company.Main.COMMAND_PARAMETER;
import static com.company.Main.COMMAND_RUN;

public class Main {
    private static final String DOT = ".";
    private static final int MAX_PAGE_PER_BATCH = 6;
    private static final int NULL_CHAPTER_INDEX = -1;
    private static final String FOLDER_SPLITTER = File.separator;
    private static final String BATCHES_OUTPUT_DIRECTORY = "batches";
    private static final String OUTPUT_FOLDER = "html";
    private static final Pattern BATCH_PATTERN = Pattern.compile("(generateHtml)\\d*-\\d*(\\.(bat|sh))");
    private static String lib = FOLDER_SPLITTER + "lib" + FOLDER_SPLITTER + "pdf2HtmlEx.exe";
    private static final int DEFAULT_MAX_THREAD = 8;
    private static final boolean DEFAULT_SEPARATE_FILE = true;
    private static String currentDirectory = "";
    private static final Map<String, Parser> ARGUMENTS;
    private static final float DEFAULT_ZOOM = 1.0f;
    private static final Parser THREAD_PARSER = new IntegerParser(DEFAULT_MAX_THREAD, 1, 50);
    private static final Parser ZOOM_PARSER = new FloatParser(DEFAULT_ZOOM, 0.1f, -1);
    private static final Parser SEPARATE_PARSER = new BooleanParser(DEFAULT_SEPARATE_FILE);
    public static final int COVER_PAGE = -1;
    public static String COMMAND_DELIM = " & ";
    public static String BATCH_FILE_EXTENSION = ".bat ";
    public static String COMMAND_RUN = "cmd";
    public static String COMMAND_PARAMETER = " /c ";


    static {
        ARGUMENTS = new HashMap<>();
        ARGUMENTS.put("-t", THREAD_PARSER);
        ARGUMENTS.put("-z", ZOOM_PARSER);
        ARGUMENTS.put("-s", SEPARATE_PARSER);
    }

    enum RESULT {
        SUCCESS,
        FAIL
    }

    enum FILE_EXTENSION {
        PDF("pdf"),
        TXT("txt");
        private String extension;

        public String getExtension() {
            return extension;
        }

        public void setExtension(String extension) {
            this.extension = extension;
        }

        private FILE_EXTENSION(final String extension) {
            this.extension = extension;
        }

        public boolean isSimilarExtension(final String givenExtension) {
            return this.extension.equalsIgnoreCase(givenExtension);
        }
    }

    private static void initCommandFormat(final boolean isLinux) {
        COMMAND_DELIM = ";";
        BATCH_FILE_EXTENSION = ".sh ";
        COMMAND_RUN = "/bin/sh/";
        COMMAND_PARAMETER = " -c ";
    }

    private static void checkArgument(String[] args) {
        int index = 2;
        while (index < args.length) {
            if(args[index].trim().equalsIgnoreCase("-l")) {
                initCommandFormat(true);
                index++;
                continue;
            }
            Parser argumentParser = ARGUMENTS.get(args[index]);
            if (argumentParser == null) {
                throw new IllegalArgumentException(String.format("Argument %1 is invalid.", args[index]));
            }

            if (index + 1 > args.length) {
                throw new IllegalArgumentException(String.format("No value for argument %1", args[index]));
            }

            try {
                argumentParser.convert(args[index + 1]);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Something wrong with args [" + args[index] + "] " + ex.getMessage
                        ());
            } catch (Exception ex) {
                throw new IllegalArgumentException(String.format("Wrong value for argument %1", args[index]));
            }
            index+=2;
        }

    }

    static private void deleteAllFiles(final String directory) {
        File batDir = new File(directory);
        if (batDir.isDirectory()) {
            File[] files = batDir.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteAllFiles(file.getAbsolutePath());
                } else {
                    file.delete();
                }
            }
        }
    }

    private static boolean isExistedFile(String filePath) {
        File file = new File(filePath);
        if (!file.isAbsolute()) {
            filePath = currentDirectory + FOLDER_SPLITTER + filePath;
        }
        return file.exists();
    }

    private static String getFilePathIfRelative(final String filePath) {
        File file = new File(filePath);
        if (!file.isAbsolute()) {
            return currentDirectory + FOLDER_SPLITTER + filePath;
        }

        return filePath;
    }

    private static void checkExtension(final String filePath, final FILE_EXTENSION expectedExtension) {
        int lastIndexOfDot = filePath.lastIndexOf(DOT);
        if (lastIndexOfDot + 1 > filePath.length()) {
            throw new IllegalArgumentException(String.format("Expected: '.%s' but got file %s", expectedExtension
                    .getExtension(), filePath));
        }
        final String extension = filePath.substring(lastIndexOfDot + 1);
        if (!expectedExtension.isSimilarExtension(extension)) {
            throw new IllegalArgumentException(String.format("Expected: '.%s' but got file %s", expectedExtension
                    .getExtension(), filePath));
        }
    }

    private static void checkInputFiles(final String[] args) {
        checkExtension(args[0], FILE_EXTENSION.TXT);
        if (!isExistedFile(args[0])) {
            if (isExistedFile(currentDirectory + FOLDER_SPLITTER + args[0])) {
                args[0] = currentDirectory + FOLDER_SPLITTER + args[0];
            } else {
                throw new IllegalArgumentException(String.format("File '%s' is not existed.", args[0]));
            }
        }
        checkExtension(args[1], FILE_EXTENSION.PDF);
        if (!isExistedFile(args[1])) {
            throw new IllegalArgumentException(String.format("File '%s' is not existed.", args[0]));
        }
    }

    private static RESULT initArgument(final String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("");
        }

        if (args[0].trim().equalsIgnoreCase("--help")) {
            throw new IllegalArgumentException("");
        }

        if (args.length < 2) {
            throw new IllegalArgumentException("");
        }

        checkInputFiles(args);
        checkArgument(args);

        if (args.length == 2) {
            return RESULT.SUCCESS;
        }

        return RESULT.SUCCESS;
    }

    private static void checkLib(final String currentDir) {
        File file = new File(currentDir + lib);
        if (!file.exists()) {
            throw new IllegalArgumentException("Do not delete any folder in this lib");
        }
    }


    public static void main(String[] args) {
        currentDirectory = System.getProperty("user.dir");
        try {
            initArgument(args);
            checkLib(currentDirectory);
        } catch (Exception ex) {
            System.out.println("************************************");
            System.out.println("Error: " + "\t" + ex.getMessage());
            printHelp();
            return;
        }

        // get file path
        args[0] = getFilePathIfRelative(args[0]);
        args[1] = getFilePathIfRelative(args[1]);

        // create command
        final List<Chapter> chapters = readAllChapter(args[0]);
        final String pdf2HtmlExLibPath = currentDirectory + lib;
        deleteAllFiles(currentDirectory + FOLDER_SPLITTER + BATCHES_OUTPUT_DIRECTORY);
        deleteAllFiles(currentDirectory + FOLDER_SPLITTER + OUTPUT_FOLDER);
        //code for generate file json
        runThreadWriteOverviewFile(currentDirectory, chapters);
        int startPage = 0, endPage = 0;
        // Render cover page.
        writeRenderPage(COVER_PAGE,
                1,
                1,
                pdf2HtmlExLibPath,
                args[1],
                currentDirectory,
                0);
        for (int chapterIndex = 0; chapterIndex < chapters.size(); chapterIndex++) {
            Chapter chapter = chapters.get(chapterIndex);
            if ((Boolean) SEPARATE_PARSER.getValue()) {
                final int totalPage = chapter.getLast() - chapter.getFirst() + 1;
                final int numOfBatches = totalPage % MAX_PAGE_PER_BATCH == 0 ?
                        totalPage / MAX_PAGE_PER_BATCH : totalPage / MAX_PAGE_PER_BATCH + 1;
                for (int batchIndex = 1; batchIndex <= numOfBatches; batchIndex++) {
                    startPage = chapter.getFirst() + (batchIndex - 1) * MAX_PAGE_PER_BATCH;
                    endPage = startPage + MAX_PAGE_PER_BATCH - 1;
                    endPage = endPage < chapter.getLast() ? endPage : chapter.getLast();
                    writeRenderPage(chapterIndex,
                            startPage,
                            endPage,
                            pdf2HtmlExLibPath,
                            args[1],
                            currentDirectory,
                            batchIndex);
                }
            } else {
                writeRenderPage(chapterIndex, chapter.getFirst(), chapter.getLast(), pdf2HtmlExLibPath, args[1],
                        currentDirectory, 1);
            }

        }
        runMultiTasks(currentDirectory, chapters);
        deleteAllFiles(currentDirectory + FOLDER_SPLITTER + BATCHES_OUTPUT_DIRECTORY);
    }

    private static void writeRenderPage(final int chapterIndex,
                                        final int startPage,
                                        final int endPage,
                                        final String pdf2HtmlExLibPath,
                                        final String pdfFilePath,
                                        final String currentDirectory,
                                        final int part) {
        try {
            final File filebat = new File(currentDirectory +
                    FOLDER_SPLITTER +
                    BATCHES_OUTPUT_DIRECTORY +
                    FOLDER_SPLITTER + "generateHtml" +
                    // TODO fix this. Change pattern if you have internet
                    (chapterIndex == COVER_PAGE ? 0 : chapterIndex) +
                    "-" +
                    part +
                    "" +
                    BATCH_FILE_EXTENSION);
            filebat.getParentFile().mkdirs();
            filebat.createNewFile();
            BufferedWriter writer = new BufferedWriter(new FileWriter(filebat));
            String zoomInString = String.valueOf((Float) ZOOM_PARSER.getValue());
            if (chapterIndex == COVER_PAGE) {
                writePage(writer, 1, 1, "cover.html", pdf2HtmlExLibPath, zoomInString,
                        currentDirectory, pdfFilePath, chapterIndex);
            } else {
                if ((Boolean) SEPARATE_PARSER.getValue()) {
                    int htmlPageIndex = (part - 1) * MAX_PAGE_PER_BATCH;
                    for (int index = 0; index + startPage <= endPage; index++) {
                        writePage(writer, index + startPage, index + startPage, (index + htmlPageIndex) + ".html",
                                pdf2HtmlExLibPath,
                                zoomInString,
                                currentDirectory, pdfFilePath, chapterIndex);
                        if ((index + startPage) != endPage) {
                            writer.append(COMMAND_DELIM);
                        }
                    }
                } else {
                    writePage(writer, startPage, endPage, chapterIndex + ".html", pdf2HtmlExLibPath, zoomInString,
                            currentDirectory, pdfFilePath, NULL_CHAPTER_INDEX);
                }
            }


            writer.flush();
        } catch (IOException e) {
            System.out.println("Has error with chapter=" + chapterIndex);
        }
    }

    private static void writePage(final BufferedWriter writer, final int firstPage, final int lastPage, final String
            outputFileName, final String pdf2HtmlExLibPath, final String zoomInString, final String currentDir,
                                  final String pdfFilePath, final int chapterIndex) throws
            IOException {
        writer.append(pdf2HtmlExLibPath);
        writer.append(" -f ");
        writer.append(firstPage + " ");
        writer.append(" -l ");
        writer.append(lastPage + " ");
        writer.append("--zoom " + zoomInString);
        writer.append(" --process-outline 0 ");
        writer.append(" --dest-dir ");
        writer.append(" " + currentDir + FOLDER_SPLITTER + OUTPUT_FOLDER);
        if (chapterIndex != NULL_CHAPTER_INDEX) {
            writer.append(FOLDER_SPLITTER + chapterIndex + " ");
        }
        writer.append(" " + pdfFilePath);
        writer.append(" " + outputFileName + " ");
    }

    private static void runThreadWriteOverviewFile(final String currentDirectory, final List<Chapter> chapters) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File fileJSON = new File(currentDirectory
                            + FOLDER_SPLITTER
                            + OUTPUT_FOLDER
                            + FOLDER_SPLITTER +
                            "output.json");
                    fileJSON.getParentFile().mkdirs();
                    fileJSON.createNewFile();
                    BufferedWriter bufferedWriter = new BufferedWriter((new FileWriter(fileJSON)));
                    bufferedWriter.append("export var data = ");
                    bufferedWriter.append("{ \"chapters\": ");
                    bufferedWriter.append("\t[ \n");
                    Iterator<Chapter> iterator = chapters.iterator();
                    int num = 0;
                    int numberOfPages;

                    while (iterator.hasNext()) {
                        Chapter chapter = iterator.next();
                        numberOfPages = (chapter.getLast() - chapter.getFirst() + 1);
                        bufferedWriter.append("\t\t{\n");
                        bufferedWriter.append("\t\t\t\"title\": \"" + chapter.getName() + "\",\n");
                        bufferedWriter.append("\t\t\t\"path\": \"/data/" + num + "\",\n");
                        bufferedWriter.append("\t\t\t\"numberOfPage\": " + numberOfPages + "\n");
                        bufferedWriter.append("\t\t}\n");
                        if (iterator.hasNext()) {
                            bufferedWriter.append(",\n");
                        }
                        num++;
                    }
                    bufferedWriter.append("\t]\n");
                    bufferedWriter.append("}");
                    bufferedWriter.flush();
                    bufferedWriter.close();
                    System.out.println("finish writing output.txt");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
    }

    private static void runMultiTasks(final String currentDirectory,
                                      final List<Chapter> chapters) {

        if (chapters != null && chapters.size() > 0) {
            List<Future> futures = new ArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool((Integer) THREAD_PARSER.getValue());
            final String directory = currentDirectory + FOLDER_SPLITTER + BATCHES_OUTPUT_DIRECTORY;
            File batchesFolder = new File(directory);
            if (batchesFolder.exists() && batchesFolder.isDirectory()) {
                if (batchesFolder.listFiles() != null) {
                    List<NewThread> threads = new ArrayList<>();
                    for (File file : batchesFolder.listFiles()) {
                        if (file.isFile() && BATCH_PATTERN.matcher(file.getName()).matches()) {
                            threads.add(new NewThread(file.getName(), directory));
                        }
                    }

                    try {
                        pool.invokeAll(threads);
                    } catch (InterruptedException ex) {
                        System.out.println("Serious Error");
                    }
                }

            }

            pool.shutdown();
            System.out.println("Finished");
        }
    }

    private static List<Chapter> readAllChapter(String link) {
        List<Chapter> chapters = new ArrayList<>();
        try {
            FileReader fileReader = new FileReader(link);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Chapter chapter = new Chapter();
                chapter.setName(line);
                String[] firstlast = bufferedReader.readLine().split(" ");
                chapter.setFirst(Integer.valueOf(firstlast[0]));
                if (firstlast.length < 2) {
                    chapter.setLast(Integer.valueOf(firstlast[0]));
                } else {
                    chapter.setLast(Integer.valueOf(firstlast[1]));
                }
                chapters.add(chapter);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {

        }
        return chapters;
    }

    private static void printHelp() {
        System.out.println("*******************************************************************************************");
        System.out.println("To use this lib run the command :");
        System.out.println("java generatelink.jar fileTxt filePdf [ optional arguments]");
        System.out.println("Usage:");
        System.out.println("fileTxt: file path of file txt which contains information of chapters of the book.");
        System.out.println("filePdf: file path of file pdf which will be converted to html");
        System.out.println("Optional arguments: ");
        System.out.println("-t numberOfThread (optional): is number of thread to handle( Default value is 8).");
        System.out.println("-z value to zoom(optional): zoom level of page( Default value is 1.0).");
        System.out.println("-s Separate each file (optional): Separate each page to a html file( Default value is 0)");
        System.out.println("-l runnning in linux");
    }

    private class Arguments {
        private String abstractFilePath;
        private String pdfFilePath;
        private int numberOfThread;

        public String getAbstractFilePath() {
            return abstractFilePath;
        }

        public void setAbstractFilePath(String abstractFilePath) {
            this.abstractFilePath = abstractFilePath;
        }

        public String getPdfFilePath() {
            return pdfFilePath;
        }

        public void setPdfFilePath(String pdfFilePath) {
            this.pdfFilePath = pdfFilePath;
        }

        public int getNumberOfThread() {
            return numberOfThread;
        }

        public void setNumberOfThread(int numberOfThread) {
            this.numberOfThread = numberOfThread;
        }
    }
}

class NewThread implements Callable<Integer> {
    private String fileName;
    private String directory;

    public NewThread(final String fileName, final String directory) {
        this.fileName = fileName;
        this.directory = directory;
    }


    private void deleteFile() {
        String url = this.directory + File.separator + this.fileName;
        File file = new File(url);
        file.deleteOnExit();
    }

    @Override
    public Integer call() throws Exception {
        final ProcessBuilder processBuilder = new ProcessBuilder(COMMAND_RUN, COMMAND_PARAMETER,
                fileName);
        processBuilder.directory(new File(this.directory));

        try {
            System.out.println("start " + fileName);
            Process process = processBuilder.start();
            System.out.println("Finish " + fileName + process.waitFor());
            process.destroy();
            return 1;
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
            return 0;
        } finally {
            deleteFile();
        }
    }
}

class Chapter {
    private String name;
    private int first;
    private int last;

    public Chapter() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getFirst() {
        return first;
    }

    public void setFirst(int first) {
        this.first = first;
    }

    public int getLast() {
        return last;
    }

    public void setLast(int last) {
        this.last = last;
    }

}

interface Parser {
    public void convert(final String valueInString);

    public Object getValue();
}

class FloatParser implements Parser {
    private Float value;
    private float maxValue = -1;
    private float minValue = -1;

    public FloatParser(final Float value) {
        this.value = value;
    }

    public FloatParser(final Float value, final float minValue, final float maxValue) {
        this.value = value;
        this.maxValue = maxValue;
        this.minValue = minValue;
    }

    public void convert(final String valueInString) {
        float value = Float.parseFloat(valueInString);
        if (maxValue != -1 && value > maxValue) {
            throw new IllegalArgumentException(String.format("the value [%s] must be less than %f", valueInString,
                    maxValue));
        }
        if (minValue != -1 && value < minValue) {
            throw new IllegalArgumentException(String.format("the value %s must be grater than %f", valueInString,
                    minValue));
        }
        this.value = value;
    }

    public Float getValue() {
        return this.value;
    }
}

class IntegerParser implements Parser {
    private Integer value;
    private int maxValue = -1;
    private int minValue = -1;

    public IntegerParser(final Integer variable) {
        this.value = variable;
    }

    public IntegerParser(final Integer defaultValue, final int minValue, final int maxValue) {
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public void convert(final String valueInString) {
        final Integer newValue = Integer.valueOf(valueInString);
        if (maxValue != -1 && value > maxValue) {
            throw new IllegalArgumentException(String.format("the value %s must be less than %d", valueInString,
                    maxValue));
        }
        if (minValue != -1 && value < minValue) {
            throw new IllegalArgumentException(String.format("the value %s must be grater than %d", valueInString,
                    minValue));
        }
        this.value = newValue;
    }

    public Integer getValue() {
        return this.value;
    }
}

class BooleanParser implements Parser {
    private Boolean value;

    public BooleanParser(final Boolean value) {
        this.value = value;
    }

    public void convert(final String valueInString) {
        this.value = Boolean.valueOf(valueInString);
    }

    public Boolean getValue() {
        return this.value;
    }
}
