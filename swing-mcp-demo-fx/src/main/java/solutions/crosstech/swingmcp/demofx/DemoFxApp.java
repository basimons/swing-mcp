package solutions.crosstech.swingmcp.demofx;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

/**
 * JavaFX counterpart of the Swing demo application.
 *
 * <p>Deliberately mirrors {@code swing-mcp-demo}: a form, a table, a list, a
 * tree, a menu and a modal dialog. Having the same surface in both toolkits is
 * what lets the integration tests assert that one command set drives either.
 * Every interactive control carries an {@code id}, because that is what a model
 * addresses it by.</p>
 */
public class DemoFxApp extends Application {

    /** Row type for the table; public accessors keep the cell factories simple. */
    public record Part(String sku, String name, int quantity) {
        public String getSku() { return sku; }
        public String getName() { return name; }
        public int getQuantity() { return quantity; }
    }

    private int canvasZoom = 100;
    private int canvasOffsetX;
    private int canvasOffsetY;

    @Override
    public void start(Stage stage) {
        TabPane tabs = new TabPane();
        tabs.setId("tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            new Tab("Form", formPane()),
            new Tab("Data", dataPane()),
            new Tab("Tree", treePane()),
            new Tab("Canvas", canvasPane()));

        BorderPane root = new BorderPane();
        root.setId("root");
        root.setTop(menuBar(stage));
        root.setCenter(tabs);

        Label status = new Label("Ready");
        status.setId("statusLabel");
        root.setBottom(status);

        stage.setScene(new Scene(root, 720, 520));
        stage.setTitle("Swing MCP JavaFX Demo");
        stage.show();
    }

    private MenuBar menuBar(Stage stage) {
        MenuItem about = new MenuItem("About");
        about.setId("aboutItem");
        about.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Used for testing the swing-mcp server.");
            alert.setTitle("About");
            alert.setHeaderText("Swing MCP JavaFX Demo");
            alert.initOwner(stage);
            alert.showAndWait();
        });
        MenuItem quit = new MenuItem("Quit");
        quit.setId("quitItem");
        quit.setOnAction(e -> stage.close());

        Menu help = new Menu("Help");
        help.getItems().addAll(about, quit);
        MenuBar bar = new MenuBar(help);
        bar.setId("menuBar");
        return bar;
    }

    private ScrollPane formPane() {
        TextField name = new TextField();
        name.setId("nameField");
        name.setPromptText("Full name");

        PasswordField password = new PasswordField();
        password.setId("passwordField");

        ComboBox<String> country = new ComboBox<>(
            FXCollections.observableArrayList("Netherlands", "South Africa", "Belgium"));
        country.setId("countryCombo");

        Spinner<Integer> quantity = new Spinner<>(1, 100, 1);
        quantity.setId("quantitySpinner");

        CheckBox subscribe = new CheckBox("Subscribe");
        subscribe.setId("subscribeCheck");

        TextArea notes = new TextArea();
        notes.setId("notesArea");
        notes.setPrefRowCount(3);

        Label result = new Label("not submitted");
        result.setId("formResult");

        Button submit = new Button("Submit");
        submit.setId("submitButton");
        submit.setOnAction(e -> result.setText(
            "submitted: " + name.getText()
                + " / " + country.getValue()
                + " / " + quantity.getValue()
                + " / subscribed=" + subscribe.isSelected()));

        VBox box = new VBox(8,
            new Label("Name"), name,
            new Label("Password"), password,
            new Label("Country"), country,
            new Label("Quantity"), quantity,
            subscribe,
            new Label("Notes"), notes,
            submit, result);
        box.setId("formBox");
        box.setPadding(new Insets(12));

        ScrollPane scroll = new ScrollPane(box);
        scroll.setId("formScroll");
        scroll.setFitToWidth(true);
        return scroll;
    }

    private VBox dataPane() {
        TableView<Part> table = new TableView<>(FXCollections.observableArrayList(
            new Part("SKU-1", "Bearing", 12),
            new Part("SKU-2", "Gasket", 48),
            new Part("SKU-3", "Filter", 7)));
        table.setId("partsTable");

        TableColumn<Part, String> sku = new TableColumn<>("SKU");
        sku.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("sku"));
        TableColumn<Part, String> partName = new TableColumn<>("Name");
        partName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        TableColumn<Part, Integer> qty = new TableColumn<>("Quantity");
        qty.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("quantity"));
        table.getColumns().addAll(java.util.List.of(sku, partName, qty));

        ListView<String> list = new ListView<>(
            FXCollections.observableArrayList("Amsterdam", "Rotterdam", "Utrecht", "Cape Town"));
        list.setId("cityList");

        VBox box = new VBox(8, new Label("Parts"), table, new Label("Cities"), list);
        box.setId("dataBox");
        box.setPadding(new Insets(12));
        return box;
    }

    private VBox treePane() {
        TreeItem<String> root = new TreeItem<>("Regions");
        TreeItem<String> europe = new TreeItem<>("Europe");
        europe.getChildren().addAll(new TreeItem<>("Netherlands"), new TreeItem<>("Belgium"));
        TreeItem<String> africa = new TreeItem<>("Africa");
        africa.getChildren().add(new TreeItem<>("South Africa"));
        root.getChildren().addAll(europe, africa);
        root.setExpanded(true);

        TreeView<String> tree = new TreeView<>(root);
        tree.setId("regionTree");

        VBox box = new VBox(8, new Label("Regions"), tree);
        box.setId("treeBox");
        box.setPadding(new Insets(12));
        return box;
    }

    private VBox canvasPane() {
        // A graph-viewer style canvas with no ScrollPane: the wheel zooms,
        // Shift+wheel pans horizontally and Ctrl+wheel pans vertically.
        Pane graph = new Pane();
        int[][] nodes = {{80, 60}, {220, 140}, {360, 80}, {160, 260}, {320, 240}};
        for (int[] n : nodes) {
            graph.getChildren().add(new Circle(n[0], n[1], 12, Color.STEELBLUE));
        }
        Pane canvas = new Pane(graph);
        canvas.setId("graphCanvas");
        canvas.setPrefSize(400, 260);
        canvas.setStyle("-fx-background-color: white;");

        Label state = new Label(canvasState());
        state.setId("canvasState");
        canvas.addEventHandler(ScrollEvent.SCROLL, e -> {
            // Shift+wheel arrives as deltaX, plain and Ctrl+wheel as deltaY.
            int notches = (int) -Math.signum(e.getDeltaY() + e.getDeltaX());
            if (e.isShiftDown()) {
                canvasOffsetX -= notches * 20;
            } else if (e.isControlDown()) {
                canvasOffsetY -= notches * 20;
            } else {
                canvasZoom = Math.max(10, Math.min(1000, canvasZoom - notches * 10));
            }
            graph.setScaleX(canvasZoom / 100.0);
            graph.setScaleY(canvasZoom / 100.0);
            graph.setTranslateX(canvasOffsetX);
            graph.setTranslateY(canvasOffsetY);
            state.setText(canvasState());
            e.consume();
        });

        ListView<String> log = new ListView<>();
        for (int i = 0; i < 200; i++) {
            log.getItems().add("Log line " + i);
        }
        log.setId("logList");
        log.setPrefHeight(120);

        VBox box = new VBox(8, canvas, state, log);
        box.setId("canvasBox");
        box.setPadding(new Insets(12));
        return box;
    }

    private String canvasState() {
        return "Canvas zoom " + canvasZoom + "%, offset (" + canvasOffsetX + "," + canvasOffsetY + ")";
    }

    public static void main(String[] args) {
        launch(args);
    }
}
