package itm.oss.splitter;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Scanner;

public class App {

  private static final String DATA_FILE = "data/expenses.csv";
  private static final String EXPORT_FILE = "data/settlement.csv";


  private final Scanner sc = new Scanner(System.in);
  private final ExpenseStore store = new ExpenseStore();
  private final Splitter splitter = new Splitter();
  private final Reports reports = new Reports();

  public static void main(String[] args) {
    new App().run();
  }

  public void run() {
    ensureDataDir();
    while (true) {
      System.out.println();
      System.out.println("=== Expense Splitter ===");
      System.out.println("1) Add expense");
      System.out.println("2) List expenses");
      System.out.println("3) Show balances");
      System.out.println("4) Suggest settlements");
      System.out.println("5) Summary by category");
      System.out.println("6) Summary by person");
      System.out.println("7) Export settlements to CSV");
      System.out.println("0) Exit");
      int choice = readInt("Select option: ");

      try {
        if (choice == 1)
          addExpenseFlow();
        else if (choice == 2)
          listExpensesFlow();
        else if (choice == 3)
          showBalancesFlow();
        else if (choice == 4)
          suggestSettlementsFlow();
        else if (choice == 5)
          summaryByCategoryFlow();
        else if (choice == 6)
          summaryByPersonFlow();
        else if (choice == 7)
          exportSettlementsFlow();
        else if (choice == 0) {
          System.out.println("Bye!");
          break;
        } else
          System.out.println("Invalid option.");
      } catch (Exception e) {
        System.out.println("Error: " + e.getMessage());
      }
    }
  }

  private void ensureDataDir() {
    File dir = new File("data");
    if (!dir.exists())
      dir.mkdirs();
  }

  private void addExpenseFlow() throws Exception {
    String date = readLine("Date (YYYY-MM-DD): ");
    String payer = readLine("Payer: ");
    BigDecimal amount = new BigDecimal(readLine("Amount: "));
    String currency = readLine("Currency (e.g., USD): ");
    String participantsStr = readLine("Participants (semicolon-separated): ");
    ArrayList<String> participants = parseParticipants(participantsStr);
    String category = readLine("Category: ");
    String notes = readLine("Notes (optional): ");

    Expense exp = new Expense(date, payer, amount, currency, participants, category, notes);
    ExpenseValidator.validate(exp);
    store.append(DATA_FILE, exp);
    System.out.println("Expense added.");
  }

  private void listExpensesFlow() throws Exception {
    ArrayList<Expense> xs = store.load(DATA_FILE);
    if (xs.isEmpty()) {
      System.out.println("(no expenses yet)");
      return;
    }
    for (int i = 0; i < xs.size(); i++) {
      Expense e = xs.get(i);
      System.out.println((i + 1) + ") " + formatExpense(e));
    }
  }

  private void showBalancesFlow() throws Exception {
    ArrayList<Expense> xs = store.load(DATA_FILE);
    Balance bal = splitter.computeBalances(xs);
    System.out.println("--- Balances (net per person) ---");
    String[] names = bal.getNames();
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      BigDecimal amt = bal.getAmount(name);
      System.out.println(pad(name, 10) + ": " + amt.setScale(2, RoundingMode.HALF_EVEN));
    }
  }

  private void suggestSettlementsFlow() throws Exception {
    ArrayList<Expense> xs = store.load(DATA_FILE);
    Balance bal = splitter.computeBalances(xs);
    ArrayList<Payment> pays = Settlement.suggest(bal);
    if (pays.isEmpty()) {
      System.out.println("Everyone is settled.");
      return;
    }
    System.out.println("--- Suggested payments ---");
    for (int i = 0; i < pays.size(); i++) {
      Payment p = pays.get(i);
      System.out.println(p.getFrom() + " -> " + p.getTo() + " : " +
          p.getAmount().setScale(2, RoundingMode.HALF_EVEN));
    }
  }

  private void summaryByCategoryFlow() throws Exception {
    ArrayList<Expense> xs = store.load(DATA_FILE);
    SimpleMap categoryTotals = reports.totalsByCategory(xs);
    System.out.println("--- Totals by category ---");
    String[] keys = categoryTotals.keys();
    for (int i = 0; i < keys.length; i++) {
      String c = keys[i];
      BigDecimal amt = categoryTotals.get(c);
      System.out.println(pad(c, 12) + ": " + amt.setScale(2, RoundingMode.HALF_EVEN));
    }
  }

  private void summaryByPersonFlow() throws Exception {
    ArrayList<Expense> xs = store.load(DATA_FILE);
    SimplePersonSummaryMap m = reports.perPerson(xs);
    System.out.println("--- Per person summary ---");
    String[] names = m.keys();
    for (int i = 0; i < names.length; i++) {
      String n = names[i];
      PersonSummary s = m.get(n);
      System.out.println(
          pad(n, 10) +
              " | paid: " + s.getPaidTotal().setScale(2, RoundingMode.HALF_EVEN) +
              " | owed: " + s.getOwedTotal().setScale(2, RoundingMode.HALF_EVEN) +
              " | net: " + s.getNet().setScale(2, RoundingMode.HALF_EVEN));
    }
  }

  private void exportSettlementsFlow() throws Exception {
    ArrayList<Expense> xs = store.load(DATA_FILE);
    Balance bal = splitter.computeBalances(xs);
    ArrayList<Payment> pays = Settlement.suggest(bal);
    Exporter.writePaymentsCsv("data/settlement.csv", pays);
    System.out.println("Exported to data/settlement.csv");
  }

  private int readInt(String prompt) {
    while (true) {
      System.out.print(prompt);
      String s = sc.nextLine().trim();
      try {
        return Integer.parseInt(s);
      } catch (NumberFormatException e) {
        System.out.println("Enter a number.");
      }
    }
  }

  private String readLine(String prompt) {
    System.out.print(prompt);
    return sc.nextLine().trim();
  }

  private ArrayList<String> parseParticipants(String s) {
    ArrayList<String> out = new ArrayList<String>();
    String[] parts = s.split(";");
    for (int i = 0; i < parts.length; i++) {
      String name = parts[i].trim();
      if (name.length() > 0)
        out.add(name);
    }
    return out;
  }

  private String formatExpense(Expense e) {
    return e.getDate() + " | " + pad(e.getPayer(), 10) + " | " +
        e.getAmount().setScale(2, RoundingMode.HALF_EVEN) + " " + e.getCurrency() +
        " | " + join(e.getParticipants(), ";") +
        " | " + e.getCategory() + " | " + e.getNotes();
  }

  private String pad(String s, int width) {
    String t = s;
    while (t.length() < width)
      t = t + " ";
    return t;
  }

  private String join(ArrayList<String> xs, String sep) {
    if (xs == null || xs.size() == 0)
      return "";
    String r = xs.get(0);
    for (int i = 1; i < xs.size(); i++)
      r = r + sep + xs.get(i);
    return r;
  }
}
