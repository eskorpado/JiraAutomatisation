function onEdit(e) {
    // Set a comment on the edited cell to indicate when it was changed.
    var range = e.range;
    if (range.getColumn() == toCol("F") || range.getColumn() == toCol("I")) {
        //range.setNote('Last modified: ' + new Date());
        var row = range.getRow();
        var column = range.getColumn();
        if (range.getValue() == "Провалено") {

            var firstSheet = SpreadsheetApp.getActive().getSheetByName("Общая информация");
            var currentSheet = SpreadsheetApp.getActive().getActiveSheet();
            var listsSheet = SpreadsheetApp.getActive().getSheetByName("Списки");
            var confSheet = SpreadsheetApp.getActive().getSheetByName("Conf");
            var sheets = [firstSheet, currentSheet, listsSheet, confSheet];

            var fix_versions = firstSheet.getRange(5, 3, 1, 1).getValue();
            var link_issue = firstSheet.getRange(2, 2, 1, 1).getValue();
            var components = firstSheet.getRange(2, 9, 1, 1).getValue();
            var fsPars = [fix_versions, link_issue, components];


            if (currentSheet.getName().indexOf("ЭФ_") == 0) {

                var rowInConf = 2;
                var csPars = efForm(sheets, row);
            } else if (currentSheet.getName().indexOf("ВИ_") == 0) {
                var rowInConf = 3;
                var csPars = viForm(sheets, row)
            }
            createWindows(sheets, row, fsPars, rowInConf, csPars);
        }
    }
}

function createWindows(sheets, row, fsPars, rowInConf, csPars) {
    var environmentCodeColumn = toCol(sheets[3].getRange(rowInConf, toCol("F"), 1, 1).getValue());
    var commentColumn = toCol(sheets[3].getRange(rowInConf, toCol("B"), 1, 1).getValue());

    var summary = fsPars[2];
    summary += " : ";
    summary += valueFromMerge(sheets[1].getRange(row, environmentCodeColumn, 1, 1));
    summary += " : ";
    summary += sheets[3].getRange(rowInConf, toCol("G"), 1, 1).getValue();

    var comment = sheets[1].getRange(row, commentColumn, 1, 1).getValue();

    var tempRange = sheets[2].getRange(1, 4, 30, 1).getValues();
    for (var i = 29; i > -1; i--) {
        if (tempRange[i][0] != "") {
            var environment = tempRange[i][0];
            break;
        }
    }

    var regeneration_steps = csPars[0];
    var expected_result = csPars[1];
    var scenario_code = csPars[2];
    var sheet_name = sheets[1].getName();
    var spreadsheet_id = sheets[1].getParent().getId();


    var template = HtmlService.createTemplateFromFile('Index');

    template.fix_versions = fsPars[0];
    template.link_issue = fsPars[1];
    template.components = fsPars[2];
    template.summary = summary;
    template.environment = environment;
    template.regeneration_steps = regeneration_steps;
    template.expected_result = expected_result;
    template.scenario_code = scenario_code;
    template.comment = comment;
    template.sheet = sheet_name;
    template.spreadsheet = spreadsheet_id;
    template.row = row;

    var userInterface = template.evaluate();
    userInterface.setWidth(700);
    userInterface.setHeight(400);

    SpreadsheetApp.getUi().showModalDialog(userInterface, "Добавить баг в Jira");
}

function viForm(sheets, row) {

    var regeneration_steps = valueFromMerge(sheets[1].getRange(row, 4, 1, 1));
    var expected_result = sheets[1].getRange(row, 5, 1, 1).getValue();
    var scenario_code = sheets[1].getRange(row, 3, 1, 1).getValue();

    return [regeneration_steps, expected_result, scenario_code];
}

function efForm(sheets, row) {

    var regeneration_steps = "- Пользователь открыл форму ";
    var temp = valueFromMerge(sheets[1].getRange(row, 1, 1, 1));
    regeneration_steps += temp.substr(0, temp.indexOf("."));
    regeneration_steps += "\n- Смотреть отображение элемента ";
    regeneration_steps += valueFromMerge(sheets[1].getRange(row, 1, 1, 1));

    var expected_result = "- элемент ";
    expected_result += sheets[1].getRange(row, 1, 1, 1).getValue();
    expected_result += " выполнен согласно требований ЧТЗ";

    var scenario_code = valueFromMerge(sheets[1].getRange(row, 1, 1, 1));

    return [regeneration_steps, expected_result, scenario_code];
}

function valueFromMerge(range) {
    if (range.isPartOfMerge()) {
        var mergedRanges = range.getMergedRanges();
        for (var i = 0; i < mergedRanges.length; i++) {
            if (mergedRanges[i].getDisplayValue().length != 0) {
                var value = (mergedRanges[i].getDisplayValue());
                break;
            }
        }
    } else {
        var value = range.getValue();
    }
    return value;
}

function toCol(letter) {
    return letter.charCodeAt(0) - 64;
}

function include(filename) {
    return HtmlService.createHtmlOutputFromFile(filename)
        .getContent();
}