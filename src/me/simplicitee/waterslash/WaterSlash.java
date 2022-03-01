package me.simplicitee.waterslash;

import java.io.File;
import java.util.Optional;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.configuration.Config;
import com.projectkorra.projectkorra.util.DamageHandler;

public class WaterSlash extends WaterAbility implements AddonAbility, Listener {
	
	private static FileConfiguration config;
	private static DustOptions particle = new DustOptions(Color.fromRGB(32, 112, 186), 0.35f);
	
	@Attribute(Attribute.DAMAGE)
	private double damage;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.SPEED)
	private double speed;
	@Attribute(Attribute.KNOCKBACK)
	private double knockback;
	@Attribute("SourceSpeed")
	private double sourceSpeed;
	
	private long prevTime;
	private boolean[] blocked = new boolean[51];
	private boolean shot = false, clicked = false, sourcing = true;
	private Vector[] dirs = new Vector[3];
	private Location[] locs = new Location[3];
	private double angle, currRange;
	private Location start;
	private int delay = 5;

	public WaterSlash(Player player, Block source) {
		super(player);
		this.damage = config.getDouble("Abilities.Water.WaterSlash.Damage");
		this.cooldown = config.getLong("Abilities.Water.WaterSlash.Cooldown");
		this.range = config.getDouble("Abilities.Water.WaterSlash.Range");
		this.speed = config.getDouble("Abilities.Water.WaterSlash.Speed");
		this.knockback = config.getDouble("Abilities.Water.WaterSlash.Knockback");
		this.sourceSpeed = config.getDouble("Abilities.Water.WaterSlash.SourceSpeed");
		this.prevTime = System.currentTimeMillis();
		this.start = source.getLocation().add(0.5, 1.1, 0.5);
		this.start();
	}

	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public Location getLocation() {
		return null;
	}

	@Override
	public String getName() {
		return "WaterSlash";
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public void progress() {
		long currTime = System.currentTimeMillis();
		double timeDelta = ((double) currTime - this.prevTime) / 1000D;
		
		Location loc = locs[1];
		if (loc == null) {
			loc = start;
		}
		
		if (GeneralMethods.isRegionProtectedFromBuild(player, loc)) {
			this.remove();
			return;
		}
		
		if (sourcing) {
			if (!player.isSneaking()) {
				this.remove();
				return;
			} else if (!bPlayer.getBoundAbilityName().equals("WaterSlash")) {
				this.remove();
				return;
			}
			
			start.add(GeneralMethods.getDirection(start, targetLocation()).normalize().multiply(sourceSpeed * timeDelta));
			
			if (!start.getBlock().isPassable()) {
				this.remove();
				return;
			}
			
			start.getWorld().spawnParticle(Particle.REDSTONE, start, 3, 0.05, 0.05, 0.05, particle);
			
			if (start.distance(targetLocation()) < 0.3) {
				sourcing = false;
			}
		} else if (!clicked) {
			if (!player.isSneaking()) {
				this.remove();
				return;
			} else if (!bPlayer.getBoundAbilityName().equals("WaterSlash")) {
				this.remove();
				return;
			}
			
			Location eye = player.getEyeLocation();
			eye.add(eye.getDirection());
			eye.getWorld().spawnParticle(Particle.REDSTONE, eye, 3, 0.05, 0.05, 0.05, particle);
		} else {
			if (--delay > 0) {
				return;
			}
			
			if (!this.shot) {
				this.shot = true;
				this.dirs[2] = player.getLocation().getDirection();
				this.dirs[1] = this.dirs[0].clone().add(this.dirs[2]).normalize();
				start = targetLocation();
	            for (int i = 0; i < 3; ++i) {
	                locs[i] = start.clone();
	            }
				this.angle = this.dirs[0].angle(this.dirs[2]);
				this.bPlayer.addCooldown(this);
			}
			
			double movement = speed * timeDelta;
			double progress = Math.min(0.2, 0.2 * movement); 
			for (double d = 0; d < movement; d += progress) {
				currRange += progress;
				if (currRange > range) {
					this.remove();
					return;
				}
				
				for (int i = 0; i < 3; ++i) {
		            locs[i].add(dirs[i].normalize().multiply(progress));
		        }
				
				for (double t = 0; t <= 1; t += 0.25 / (angle * currRange)) {
					if (blocked[(int) Math.round(t * (blocked.length - 1))]) {
		                continue;
		            }
					
					Location display = bezier(t);
					display.setDirection(GeneralMethods.getDirection(start, display));
					if (rayCast(display, progress)) {
						blocked[(int) Math.round(t * (blocked.length - 1))] = true;
					}
					
					display.getWorld().spawnParticle(Particle.REDSTONE, display, 1, 0, 0, 0, particle);
					
					/*
					if (TempBlock.isTempBlock(display.getBlock())) {
						continue;
					}
					
					new TempBlock(display.getBlock(), GeneralMethods.getWaterData(0), 100);
					*/
				}
			}
		}
		
		this.prevTime = currTime;
	}

	@Override
	public String getAuthor() {
		return "Simplicitee";
	}

	@Override
	public String getVersion() {
		return "1.0.0";
	}
	
	@Override
	public String getDescription() {
		return "Create a slashing attack of water!";
	}
	
	@Override
	public String getInstructions() {
		return "Sneak at water and click after it has reached you, then quickly drag your mouse to create a slash";
	}

	@Override
	public void load() {
		Config c = new Config(new File("Simplicitee.yml"));
		config = c.get();
		
		config.addDefault("Abilities.Water.WaterSlash.Damage", 3.0);
		config.addDefault("Abilities.Water.WaterSlash.Range", 14);
		config.addDefault("Abilities.Water.WaterSlash.Cooldown", 5000);
		config.addDefault("Abilities.Water.WaterSlash.Speed", 20);
		config.addDefault("Abilities.Water.WaterSlash.Knockback", 0.8);
		config.addDefault("Abilities.Water.WaterSlash.SourceSpeed", 5);
		
		c.save();
		ProjectKorra.plugin.getServer().getPluginManager().registerEvents(this, ProjectKorra.plugin);
	}

	@Override
	public void stop() {
	}

	private Location bezier(double t) {
        return locs[1].clone().add(locs[0].clone().subtract(locs[1]).multiply((1 - t) * (1 - t))).add(locs[2].clone().subtract(locs[1]).multiply(t * t));
    }

    private boolean rayCast(Location loc, double dist) {
        Vector kb = loc.getDirection().setY(0).multiply(knockback);
        Optional.ofNullable(loc.getWorld().rayTrace(loc, loc.getDirection(), dist, FluidCollisionMode.NEVER, true, 0.4, null))
        .map(RayTraceResult::getHitEntity)
        .filter(e -> e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId()))
        .ifPresent(e -> {
            DamageHandler.damageEntity(e, damage, this);
            e.setVelocity(kb);
        });

        return Optional.ofNullable(loc.getWorld().rayTraceBlocks(loc, loc.getDirection(), dist, FluidCollisionMode.NEVER)).filter(ray -> ray.getHitBlock() != null).isPresent();    
    }
	
    public void click() {
    	if (clicked) return;
    	this.clicked = true;
		this.dirs[0] = player.getLocation().getDirection();
    }
    
    public Location targetLocation() {
    	return player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.7));
    }
    
    @EventHandler
    private void onSneak(PlayerToggleSneakEvent event) {
    	BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(event.getPlayer());
    	if (bPlayer == null || !event.isSneaking()) {
    		return;
    	}
    	
    	if (bPlayer.isOnCooldown("WaterSlash")) {
    		return;
    	} else if (!bPlayer.getBoundAbilityName().equals("WaterSlash")) {
    		return;
    	}
    	
    	Block source = WaterAbility.getWaterSourceBlock(event.getPlayer(), 7, false);
    	if (source == null) {
    		return;
    	}
    	
    	new WaterSlash(event.getPlayer(), source);
    }
    
    @EventHandler
    private void onClick(PlayerInteractEvent event) {
    	if (event.getHand() != EquipmentSlot.HAND) {
			return;
		} else if (event.getAction() == Action.LEFT_CLICK_AIR || (event.getAction() == Action.LEFT_CLICK_BLOCK && event.useInteractedBlock() != Event.Result.DENY)) {
			if (!CoreAbility.hasAbility(event.getPlayer(), WaterSlash.class)) {
	    		return;
	    	}
	    	
	    	CoreAbility.getAbility(event.getPlayer(), WaterSlash.class).click();
		}
    }
}
