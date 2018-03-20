package com.company;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

public class Main {
    private static String lib = "\\lib\\pdf2HtmlEx.exe";
    private static final int MAX_THREAD = 8;
    private static final int MAX_PAGE_PER_BATCH = 6;
    private static final String FOLDER_SPLITTER = "\\";
    private static final String BATCHES_OUTPUT_DIRECTORY = "batches";
    private static final Pattern BATCH_PATTERN = Pattern.compile("(generateHtml)\\d*-\\d*(\\.bat)");
    private static final String OUTPUT_FOLDER = "htmls";

    public static void main(String[] args) {
        String currentDirectory = System.getProperty("user.dir");

        // create command
        final List<Chapter> chapters = readAllChapter(args[0]);
        final String pdf2HtmlExLibPath = currentDirectory + lib;
        deleteAllFileBatch(currentDirectory + FOLDER_SPLITTER + OUTPUT_FOLDER);
        //code for generate file json
        runThreadWriteOverviewFile(currentDirectory, chapters);

        for (int chapterIndex = 0; chapterIndex < chapters.size(); chapterIndex++) {
            Chapter chapter = chapters.get(chapterIndex);
            int startPage = 0, endPage = 0;
            final int totalPage = chapter.getLast() - chapter.getFirst() + 1;
            final int numOfBatches = totalPage % MAX_PAGE_PER_BATCH == 0 ?
                    totalPage / MAX_PAGE_PER_BATCH : totalPage / MAX_PAGE_PER_BATCH + 1;
            for (int batchIndex = 0; batchIndex < numOfBatches; batchIndex++) {
                startPage = chapter.getFirst() + batchIndex * MAX_PAGE_PER_BATCH;
                endPage  = startPage + MAX_PAGE_PER_BATCH -1;
                endPage = endPage < chapter.getLast() ? endPage : chapter.getLast();
                writeRenderPage(chapterIndex,
                        startPage ,
                        endPage,
                        pdf2HtmlExLibPath,
                        args[1],
                        currentDirectory,
                        batchIndex,
                        chapterIndex == 0 && batchIndex == 0);
            }
        }
        runMultiTasks(currentDirectory, chapters);
    }

    private static void writeRenderPage(final int chapterIndex,
                                        final int startPage,
                                        final int endPage,
                                        final String pdf2HtmlExLibPath,
                                        final String pdfFilePath,
                                        final String currentDirectory,
                                        final int part,
                                        final boolean isWriteCover) {
        try {
            final File filebat = new File(currentDirectory +
                    FOLDER_SPLITTER +
                    BATCHES_OUTPUT_DIRECTORY +
                    "\\generateHtml" +
                    chapterIndex +
                    "-" +
                    part +
                    "" +
                    ".bat");
            filebat.getParentFile().mkdirs();
            filebat.createNewFile();
            BufferedWriter writer = new BufferedWriter(new FileWriter(filebat));
            int htmlPageIndex = part * MAX_PAGE_PER_BATCH;
            if (isWriteCover) {
                writer.append(pdf2HtmlExLibPath);
                writer.append(" -f ");
                writer.append(1 + " ");
                writer.append(" -l ");
                writer.append(1 + " ");
                writer.append(" --process-outline 0 ");
                writer.append(" --dest-dir ");
                writer.append(currentDirectory + FOLDER_SPLITTER + OUTPUT_FOLDER + " ");
                writer.append(pdfFilePath + " ");
                writer.append("cover.html");
                writer.append(" & ");
            }
            for (int index = startPage; index <= endPage; index++) {
                writer.append(pdf2HtmlExLibPath);
                writer.append(" -f ");
                writer.append(index + " ");
                writer.append(" -l ");
                writer.append(index + " ");
                writer.append(" --process-outline 0 ");
                writer.append(" --dest-dir ");
                writer.append(currentDirectory + FOLDER_SPLITTER + OUTPUT_FOLDER + "\\" + chapterIndex + " ");
                writer.append(pdfFilePath + " ");
                writer.append(htmlPageIndex++ + ".html");
                if (index != endPage) {
                    writer.append(" & ");
                }
            }
            writer.flush();
        } catch (IOException e) {
            System.out.println("Has error with chapter=" + chapterIndex);
        }
    }

    private static void runThreadWriteOverviewFile(final String currentDirectory, final List<Chapter> chapters) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File fileJSON = new File(currentDirectory + FOLDER_SPLITTER + OUTPUT_FOLDER + FOLDER_SPLITTER +
                            "output.json");
                    fileJSON.getParentFile().mkdirs();
                    fileJSON.createNewFile();
                    BufferedWriter bufferedWriter = new BufferedWriter((new FileWriter(fileJSON)));
                    bufferedWriter.append("export var data = ");
                    bufferedWriter.append("{ pages: ");
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
                        bufferedWriter.append("\t\t\t\"numberOfPage\": " +  numberOfPages + "\n");
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
            ExecutorService pool = Executors.newFixedThreadPool(MAX_THREAD);
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

                    try{
                        pool.invokeAll(threads);
                    } catch(InterruptedException ex) {
                        System.out.println("Serious Error");
                    }


                }

            }

            pool.shutdown();
            System.out.println("Finished");
        }
    }

    static private void deleteAllFileBatch(final String batchesDirectory) {
        File batDir = new File(batchesDirectory);
        if (batDir.isDirectory()) {
            File[] files = batDir.listFiles();
            for (File file : files) {
                file.delete();
            }
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

    private void readArguments(String[] args) {


    }

    private void printHelp(){
        System.out.println("Usage:\n");
        System.out.println("-t: (optional):\n");
    }

    private class Arguments{
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
        File file = new File(this.directory + "\\" + this.fileName);
        file.delete();
    }

    @Override
    public Integer call() throws Exception {
        final ProcessBuilder processBuilder = new ProcessBuilder("cmd ", " /c ",
                fileName);
        processBuilder.directory(new File(this.directory));

        try {
            System.out.println("start " + fileName);
            Process process = processBuilder.start();
            System.out.println("Finish " + fileName + process.waitFor());
            process.destroy();
            deleteFile();
            return 1;
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
            return 0;
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
