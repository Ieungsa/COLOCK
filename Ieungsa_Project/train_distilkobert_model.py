import os
os.environ["CUDA_VISIBLE_DEVICES"] = "-1" # Force CPU usage
import pandas as pd
import tensorflow as tf # Import tensorflow first
from sklearn.model_selection import train_test_split
from transformers import BertTokenizer, TFAutoModelForSequenceClassification
from datasets import Dataset
import numpy as np
from sklearn.metrics import accuracy_score, precision_recall_fscore_support
import shutil

print("Starting DistilKoBERT training and TFLite conversion script.")

# --- Configuration ---
MODEL_NAME = "monologg/distilkobert"
MAX_LENGTH = 128 # Max length for tokenization, as suggested in the guide
BATCH_SIZE = 16
NUM_EPOCHS = 3
LEARNING_RATE = 2e-5
OUTPUT_DIR = "new_korean_model_distilkobert"
TFLITE_MODEL_NAME = "phishing_detector_distilkobert.tflite"
VOCAB_FILE_NAME = "vocab.txt"
ASSETS_DIR = "app/src/main/assets"

# --- Step 1: Load and Prepare Data ---
print("Loading Korean vishing dataset...")
try:
    vishing_file = 'vishing_dataset_repo/Data_Collection_Preprocessing/df_data_vishing.csv'
    unharmful_file = 'vishing_dataset_repo/Data_Collection_Preprocessing/df_data_unharmful.csv'

    df_vishing = pd.read_csv(vishing_file)
    df_unharmful = pd.read_csv(unharmful_file)

    df_vishing['label'] = 1 # 1 for spam/vishing
    df_unharmful['label'] = 0 # 0 for ham/normal

    df = pd.concat([df_vishing, df_unharmful], ignore_index=True)
    df.dropna(subset=['transcript'], inplace=True)
    
    # Shuffle the dataset
    df = df.sample(frac=1, random_state=42).reset_index(drop=True)

    print("Dataset loaded successfully.")
except FileNotFoundError as e:
    print(f"Error: Dataset files not found. {e}")
    exit(1)

# Split data
X = df['transcript'].tolist()
y = df['label'].tolist()
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)

# Create Hugging Face Dataset objects
train_dataset = Dataset.from_dict({'text': X_train, 'label': y_train})
test_dataset = Dataset.from_dict({'text': X_test, 'label': y_test})

print(f"\nTraining set size: {len(train_dataset)}")
print(f"Testing set size: {len(test_dataset)}")

# --- Step 2: Initialize Tokenizer and Model ---
print(f"\nInitializing tokenizer and model from {MODEL_NAME}...")
tokenizer = BertTokenizer.from_pretrained(MODEL_NAME)
model = TFAutoModelForSequenceClassification.from_pretrained(MODEL_NAME, num_labels=2)

# --- Step 3: Tokenize Data ---
def tokenize_function(examples):
    return tokenizer(examples["text"], truncation=True, padding="max_length", max_length=MAX_LENGTH)

tokenized_train_dataset = train_dataset.map(tokenize_function, batched=True)
tokenized_test_dataset = test_dataset.map(tokenize_function, batched=True)

# Remove the original "text" column as it's no longer needed for training
tokenized_train_dataset = tokenized_train_dataset.remove_columns(["text"])
tokenized_test_dataset = tokenized_test_dataset.remove_columns(["text"])

# Prepare for TensorFlow training
tf_train_dataset = tokenized_train_dataset.to_tf_dataset(
    columns=["attention_mask", "input_ids"],
    label_cols=["label"],
    shuffle=True,
    batch_size=BATCH_SIZE,
)

tf_test_dataset = tokenized_test_dataset.to_tf_dataset(
    columns=["attention_mask", "input_ids"],
    label_cols=["label"],
    shuffle=False,
    batch_size=BATCH_SIZE,
)

# --- Step 4: Fine-tune DistilKoBERT ---
print("\nFine-tuning DistilKoBERT model...")
optimizer = tf.keras.optimizers.Adam(learning_rate=LEARNING_RATE)
model.compile(optimizer=optimizer,
              loss=tf.keras.losses.SparseCategoricalCrossentropy(from_logits=True),
              metrics=['accuracy'])

model.fit(tf_train_dataset, epochs=NUM_EPOCHS)

# --- Step 5: Evaluate the Model ---
print("\nEvaluating model...")
predictions = model.predict(tf_test_dataset).logits
predicted_labels = np.argmax(predictions, axis=1)

true_labels = []
for batch in tf_test_dataset:
    true_labels.extend(batch[1].numpy()) # batch[1] contains the labels

accuracy = accuracy_score(true_labels, predicted_labels)
precision, recall, f1, _ = precision_recall_fscore_support(true_labels, predicted_labels, average='binary')

print(f"Accuracy: {accuracy:.4f}")
print(f"Precision: {precision:.4f}")
print(f"Recall: {recall:.4f}")
print(f"F1 Score: {f1:.4f}")

# --- Step 6: Save the Fine-tuned Model ---
if not os.path.exists(OUTPUT_DIR):
    os.makedirs(OUTPUT_DIR)
model.save_pretrained(OUTPUT_DIR)
tokenizer.save_pretrained(OUTPUT_DIR)
print(f"\nFine-tuned model and tokenizer saved to {OUTPUT_DIR}")

# --- Step 7: Convert to TFLite ---
print("\nConverting model to TFLite...")

# Save the model in a format TFLiteConverter can understand (SavedModel format)
# Hugging Face TF models can be directly converted if they are saved as SavedModel
# However, TFAutoModelForSequenceClassification.from_pretrained loads a Keras model
# which can be directly converted.
# Let's ensure we have a SavedModel format for robust conversion.
# A common way is to re-load and save as SavedModel or directly convert the Keras model.

# The model.save_pretrained(OUTPUT_DIR) saves it in a format that includes a Keras model.
# We can directly use the saved Keras model for conversion.
# The `model` object itself is a Keras model.

# Create a concrete function for the TFLite converter
# This is crucial for models with dynamic input shapes or complex operations
@tf.function(input_signature=[
    tf.TensorSpec(shape=[None, MAX_LENGTH], dtype=tf.int32, name='input_ids'),
    tf.TensorSpec(shape=[None, MAX_LENGTH], dtype=tf.int32, name='attention_mask')
])
def serving_fn(input_ids, attention_mask):
    # The model expects a dictionary of inputs
    inputs = {
        'input_ids': input_ids,
        'attention_mask': attention_mask
    }
    # The model returns a dictionary with 'logits'
    return model(inputs).logits

# Save the serving function as a SavedModel
saved_model_path = os.path.join(OUTPUT_DIR, "saved_model")
tf.saved_model.save(model, saved_model_path, signatures={'serving_default': serving_fn})

converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_path)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16] # Use float16 for quantization
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS # Required for some TF operations not natively supported by TFLite
]

tflite_model = converter.convert()
tflite_output_path = os.path.join(OUTPUT_DIR, TFLITE_MODEL_NAME)
with open(tflite_output_path, 'wb') as f:
    f.write(tflite_model)

print(f"TFLite model saved to {tflite_output_path}")

# --- Step 8: Prepare Vocabulary File ---
print(f"\nPreparing vocabulary file ({VOCAB_FILE_NAME})...")
vocab = tokenizer.get_vocab()
# Sort vocab by token ID to ensure consistent order
sorted_vocab = sorted(vocab.items(), key=lambda item: item[1])

vocab_output_path = os.path.join(OUTPUT_DIR, VOCAB_FILE_NAME)
with open(vocab_output_path, 'w', encoding='utf-8') as f:
    for token, _ in sorted_vocab:
        f.write(token + '\n')

print(f"Vocabulary file saved to {vocab_output_path}")

# --- Step 9: Copy TFLite model and vocab to Android assets ---
print(f"\nCopying {TFLITE_MODEL_NAME} and {VOCAB_FILE_NAME} to Android assets directory ({ASSETS_DIR})...")
if not os.path.exists(ASSETS_DIR):
    os.makedirs(ASSETS_DIR)

shutil.copy(tflite_output_path, os.path.join(ASSETS_DIR, TFLITE_MODEL_NAME))
shutil.copy(vocab_output_path, os.path.join(ASSETS_DIR, VOCAB_FILE_NAME))

print("DistilKoBERT training, TFLite conversion, and asset deployment finished successfully.")
