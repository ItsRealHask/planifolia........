package dev.mcextra.config;

public class Profile {
	public String name = "Профиль";
	public Bind[] binds = new Bind[ModConfig.BIND_COUNT];

	public Profile() {
		normalize();
	}

	public Profile(String name) {
		this.name = name;
		normalize();
	}

	/** Гарантирует ровно BIND_COUNT непустых биндов (после чтения из JSON). */
	public void normalize() {
		if (name == null) {
			name = "Профиль";
		}
		Bind[] fixed = new Bind[ModConfig.BIND_COUNT];
		for (int i = 0; i < fixed.length; i++) {
			fixed[i] = (binds != null && i < binds.length && binds[i] != null) ? binds[i] : new Bind();
		}
		binds = fixed;
	}
}
