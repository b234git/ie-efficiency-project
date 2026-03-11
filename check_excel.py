import pandas as pd
import json

file_path = r"c:\Users\mphat\Desktop\work\IE-Eff\info\EFF FEB V6.xlsx"
df = pd.read_excel(file_path, sheet_name="SEW", skiprows=2)

records = df[df["ARTICLE"] == "398846"]
for i, row in records.iterrows():
    if pd.notna(row["DATE"]) and "2026-02-02" in str(row["DATE"]):
        print("Date:", row["DATE"])
        print("Line:", row["LINE"])
        print("Article:", row["ARTICLE"])
        print("Output:", row["OUTPUT"])
        print("MP:", row["MP"])
        print("WT:", row["WT"])
        print("DLI:", row["DLI"])
        print("Target (EFF Salary Target?):", row["TARGET"]) 
        # Identify exact columns for EFF, PPH
        print("EFF Salary% :", row.get("EFF.1")) # often there are multiple EFF cols
        print("EFF KPI% :", row.get("EFF")) 
        
        # Let's print out all column keys and values for this row to trace the formula
        for col in df.columns:
            if "EFF" in str(col) or "PPH" in str(col) or "TARGET" in str(col) or "QUOTA" in str(col):
                print(f"Col {col} = {row[col]}")
        print("---------")
