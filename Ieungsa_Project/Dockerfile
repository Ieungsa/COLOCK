FROM tensorflow/tensorflow:2.13.0

WORKDIR /app

COPY requirements.txt .

# scikit-learn is not pre-installed in the TF image
RUN pip install --no-cache-dir -r requirements.txt

COPY train_model_keras.py .

CMD ["python", "train_model_keras.py"]