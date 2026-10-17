package dev.mcextra.config;

/** Один бинд: «эта клавиша/кнопка» -> «нажать это действие игры». */
public class Bind {
	/** null = не назначено. */
	public BindInput input;
	/** Имя ванильного действия (например "key.jump"), null = не выбрано. */
	public String target;
}
