import pandas as pd

file_path = r"c:\Users\mphat\Desktop\work\IE-Eff\info\EFF FEB V6.xlsx"
df = pd.read_excel(file_path, sheet_name="D", skiprows=2)

records = df[df["ARTICLE"] == 398846]
if len(records) == 0:
    records = df[df["ARTICLE"] == "398846"]

for i, row in records.iterrows():
    if pd.notna(row["DATE"]) and "2026-02-02" in str(row["DATE"]):
        print("Date:", row["DATE"])
        print("Line:", row["LINE"])
        print("Article:", row["ARTICLE"])
        print("Output:", row["OUTPUT.1"])
        print("MP:", row["MP"])
        print("WT:", row["WT"])
        print("DLI:", row.get("DLI"))
        
        # Let's print out all column keys and values for this row to trace the formula
        for col in df.columns:
            if any(k in str(col).upper() for k in ["EFF", "PPH", "TARGET", "QUOTA", "OUTPUT", "CT"]):
                print(f"Col {col} = {row[col]}")
        print("---------")
